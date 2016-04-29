/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.core.mgmt.rebind;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.ExecutionContext;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.mgmt.ha.ManagementNodeState;
import org.apache.brooklyn.api.mgmt.ha.MementoCopyMode;
import org.apache.brooklyn.api.mgmt.rebind.ChangeListener;
import org.apache.brooklyn.api.mgmt.rebind.PersistenceExceptionHandler;
import org.apache.brooklyn.api.mgmt.rebind.RebindExceptionHandler;
import org.apache.brooklyn.api.mgmt.rebind.RebindManager;
import org.apache.brooklyn.api.mgmt.rebind.mementos.BrooklynMementoPersister;
import org.apache.brooklyn.api.mgmt.rebind.mementos.BrooklynMementoRawData;
import org.apache.brooklyn.api.mgmt.rebind.mementos.TreeNode;
import org.apache.brooklyn.api.objs.BrooklynObject;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.BrooklynFeatureEnablement;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.enricher.AbstractEnricher;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.mgmt.ha.HighAvailabilityManagerImpl;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.core.mgmt.persist.BrooklynMementoPersisterToObjectStore;
import org.apache.brooklyn.core.mgmt.persist.BrooklynPersistenceUtils;
import org.apache.brooklyn.core.mgmt.persist.PersistenceActivityMetrics;
import org.apache.brooklyn.core.mgmt.persist.BrooklynPersistenceUtils.CreateBackupMode;
import org.apache.brooklyn.core.mgmt.rebind.transformer.CompoundTransformer;
import org.apache.brooklyn.core.server.BrooklynServerConfig;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.QuorumCheck;
import org.apache.brooklyn.util.collections.QuorumCheck.QuorumChecks;
import org.apache.brooklyn.util.core.task.BasicExecutionContext;
import org.apache.brooklyn.util.core.task.ScheduledTask;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.exceptions.RuntimeInterruptedException;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/** Manages the persistence/rebind process.
 * <p>
 * Lifecycle is to create an instance of this, set it up (e.g. {@link #setPeriodicPersistPeriod(Duration)}, 
 * {@link #setPersister(BrooklynMementoPersister)}; however noting that persist period must be set before the persister).
 * <p>
 * Usually done for you by the conveniences (such as the launcher). */
public class RebindManagerImpl implements RebindManager {

    // TODO Use ImmediateDeltaChangeListener if the period is set to 0?
    
    public static final ConfigKey<RebindFailureMode> DANGLING_REFERENCE_FAILURE_MODE =
            ConfigKeys.newConfigKey(RebindFailureMode.class, "rebind.failureMode.danglingRef",
                    "Action to take if a dangling reference is discovered during rebind", RebindFailureMode.CONTINUE);
    public static final ConfigKey<RebindFailureMode> REBIND_FAILURE_MODE =
            ConfigKeys.newConfigKey(RebindFailureMode.class, "rebind.failureMode.rebind",
                    "Action to take if a failure occurs during rebind", RebindFailureMode.FAIL_AT_END);
    public static final ConfigKey<RebindFailureMode> ADD_CONFIG_FAILURE_MODE =
            ConfigKeys.newConfigKey(RebindFailureMode.class, "rebind.failureMode.addConfig",
                    "Action to take if a failure occurs when setting a config value. It could happen coercion of the value type to fail.", RebindFailureMode.FAIL_AT_END);
    public static final ConfigKey<RebindFailureMode> ADD_POLICY_FAILURE_MODE =
            ConfigKeys.newConfigKey(RebindFailureMode.class, "rebind.failureMode.addPolicy",
                    "Action to take if a failure occurs when adding a policy or enricher", RebindFailureMode.CONTINUE);
    public static final ConfigKey<RebindFailureMode> LOAD_POLICY_FAILURE_MODE =
            ConfigKeys.newConfigKey(RebindFailureMode.class, "rebind.failureMode.loadPolicy",
                    "Action to take if a failure occurs when loading a policy or enricher", RebindFailureMode.CONTINUE);

    public static final ConfigKey<QuorumCheck> DANGLING_REFERENCES_MIN_REQUIRED_HEALTHY =
        ConfigKeys.newConfigKey(QuorumCheck.class, "rebind.failureMode.danglingRefs.minRequiredHealthy",
                "Number of items which must be rebinded at various sizes; "
                + "a small number of dangling references is possible if items are in the process of being created or deleted, "
                + "and that should be resolved on retry; the default set here allows max 2 dangling up to 10 items, "
                + "then linear regression to allow max 5% at 100 items and above", 
                QuorumChecks.newLinearRange("[[0,-2],[10,8],[100,95],[200,190]]"));

    public static final Logger LOG = LoggerFactory.getLogger(RebindManagerImpl.class);

    private final ManagementContextInternal managementContext;
    
    private volatile Duration periodicPersistPeriod = Duration.ONE_SECOND;
    
    private volatile boolean persistenceRunning = false;
    private volatile PeriodicDeltaChangeListener persistenceRealChangeListener;
    private volatile ChangeListener persistencePublicChangeListener;
    
    private volatile boolean readOnlyRunning = false;
    private volatile ScheduledTask readOnlyTask = null;
    private transient Semaphore rebindActive = new Semaphore(1);
    private transient AtomicInteger readOnlyRebindCount = new AtomicInteger(Integer.MIN_VALUE);
    
    private volatile BrooklynMementoPersister persistenceStoreAccess;

    final boolean persistPoliciesEnabled;
    final boolean persistEnrichersEnabled;
    final boolean persistFeedsEnabled;
    final boolean persistCatalogItemsEnabled;
    
    private RebindFailureMode danglingRefFailureMode;
    private RebindFailureMode rebindFailureMode;
    private RebindFailureMode addConfigFailureMode;
    private RebindFailureMode addPolicyFailureMode;
    private RebindFailureMode loadPolicyFailureMode;
    private QuorumCheck danglingRefsQuorumRequiredHealthy;
    
    private boolean isAwaitingInitialRebind;
    
    private PersistenceActivityMetrics rebindMetrics = new PersistenceActivityMetrics();
    private PersistenceActivityMetrics persistMetrics = new PersistenceActivityMetrics();

    Integer firstRebindAppCount, firstRebindEntityCount, firstRebindItemCount;
    
    /**
     * For tracking if rebinding, for {@link AbstractEnricher#isRebinding()} etc.
     *  
     * TODO What is a better way to do this?!
     * 
     * @author aled
     */
    @Beta
    public static class RebindTracker {
        private static ThreadLocal<Boolean> rebinding = new ThreadLocal<Boolean>();
        
        public static boolean isRebinding() {
            return (rebinding.get() == Boolean.TRUE);
        }
        
        static void reset() {
            rebinding.set(Boolean.FALSE);
        }
        
        static void setRebinding() {
            rebinding.set(Boolean.TRUE);
        }
    }

    public RebindManagerImpl(ManagementContextInternal managementContext) {
        this.managementContext = managementContext;
        this.persistencePublicChangeListener = ChangeListener.NOOP;
        
        this.persistPoliciesEnabled = BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_POLICY_PERSISTENCE_PROPERTY);
        this.persistEnrichersEnabled = BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_ENRICHER_PERSISTENCE_PROPERTY);
        this.persistFeedsEnabled = BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_FEED_PERSISTENCE_PROPERTY);
        this.persistCatalogItemsEnabled = BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_CATALOG_PERSISTENCE_PROPERTY);

        danglingRefFailureMode = managementContext.getConfig().getConfig(DANGLING_REFERENCE_FAILURE_MODE);
        rebindFailureMode = managementContext.getConfig().getConfig(REBIND_FAILURE_MODE);
        addConfigFailureMode = managementContext.getConfig().getConfig(ADD_CONFIG_FAILURE_MODE);
        addPolicyFailureMode = managementContext.getConfig().getConfig(ADD_POLICY_FAILURE_MODE);
        loadPolicyFailureMode = managementContext.getConfig().getConfig(LOAD_POLICY_FAILURE_MODE);
        
        danglingRefsQuorumRequiredHealthy = managementContext.getConfig().getConfig(DANGLING_REFERENCES_MIN_REQUIRED_HEALTHY);

        LOG.debug("{} initialized, settings: policies={}, enrichers={}, feeds={}, catalog={}",
                new Object[]{this, persistPoliciesEnabled, persistEnrichersEnabled, persistFeedsEnabled, persistCatalogItemsEnabled});
    }

    public ManagementContextInternal getManagementContext() {
        return managementContext;
    }
    
    /**
     * Must be called before setPerister()
     */
    public void setPeriodicPersistPeriod(Duration period) {
        if (persistenceStoreAccess!=null) throw new IllegalStateException("Cannot set period after persister is generated.");
        this.periodicPersistPeriod = period;
    }

    /**
     * @deprecated since 0.7.0; use {@link #setPeriodicPersistPeriod(Duration)}
     */
    public void setPeriodicPersistPeriod(long periodMillis) {
        setPeriodicPersistPeriod(Duration.of(periodMillis, TimeUnit.MILLISECONDS));
    }

    public boolean isPersistenceRunning() {
        return persistenceRunning;
    }
    
    public boolean isReadOnlyRunning() {
        return readOnlyRunning;
    }
    
    @Override
    public void setPersister(BrooklynMementoPersister val) {
        PersistenceExceptionHandler exceptionHandler = PersistenceExceptionHandlerImpl.builder()
                .build();
        setPersister(val, exceptionHandler);
    }

    @Override
    public void setPersister(BrooklynMementoPersister val, PersistenceExceptionHandler exceptionHandler) {
        if (persistenceStoreAccess != null && persistenceStoreAccess != val) {
            throw new IllegalStateException("Dynamically changing persister is not supported: old="+persistenceStoreAccess+"; new="+val);
        }
        if (persistenceRealChangeListener!=null) {
            // TODO should probably throw here, but previously we have not -- so let's log for now to be sure it's not happening
            LOG.warn("Persister reset after listeners have been set", new Throwable("Source of persister reset"));
        }
        
        this.persistenceStoreAccess = checkNotNull(val, "persister");
        
        this.persistenceRealChangeListener = new PeriodicDeltaChangeListener(managementContext.getServerExecutionContext(), persistenceStoreAccess, exceptionHandler, persistMetrics, periodicPersistPeriod);
        this.persistencePublicChangeListener = new SafeChangeListener(persistenceRealChangeListener);
        
        if (persistenceRunning) {
            persistenceRealChangeListener.start();
        }
    }

    @Override
    @VisibleForTesting
    public BrooklynMementoPersister getPersister() {
        return persistenceStoreAccess;
    }
    
    @Override
    public void startPersistence() {
        if (readOnlyRunning) {
            throw new IllegalStateException("Cannot start read-only when already running with persistence");
        }
        LOG.debug("Starting persistence ("+this+"), mgmt "+managementContext.getManagementNodeId());
        if (!persistenceRunning) {
            if (managementContext.getBrooklynProperties().getConfig(BrooklynServerConfig.PERSISTENCE_BACKUPS_REQUIRED_ON_PROMOTION)) {
                BrooklynPersistenceUtils.createBackup(managementContext, CreateBackupMode.PROMOTION, MementoCopyMode.REMOTE);
            }
        }
        persistenceRunning = true;
        readOnlyRebindCount.set(Integer.MIN_VALUE);
        persistenceStoreAccess.enableWriteAccess();
        if (persistenceRealChangeListener != null) persistenceRealChangeListener.start();
    }

    @Override
    public void stopPersistence() {
        LOG.debug("Stopping persistence ("+this+"), mgmt "+managementContext.getManagementNodeId());
        persistenceRunning = false;
        if (persistenceRealChangeListener != null) persistenceRealChangeListener.stop();
        if (persistenceStoreAccess != null) persistenceStoreAccess.disableWriteAccess(true);
        LOG.debug("Stopped rebind (persistence), mgmt "+managementContext.getManagementNodeId());
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public void startReadOnly(final ManagementNodeState mode) {
        if (!ManagementNodeState.isHotProxy(mode)) {
            throw new IllegalStateException("Read-only rebind thread only permitted for hot proxy modes; not "+mode);
        }
        
        if (persistenceRunning) {
            throw new IllegalStateException("Cannot start read-only when already running with persistence");
        }
        if (readOnlyRunning || readOnlyTask!=null) {
            LOG.warn("Cannot request read-only mode for "+this+" when already running - "+readOnlyTask+"; ignoring");
            return;
        }
        LOG.debug("Starting read-only rebinding ("+this+"), mgmt "+managementContext.getManagementNodeId());
        
        if (persistenceRealChangeListener != null) persistenceRealChangeListener.stop();
        if (persistenceStoreAccess != null) persistenceStoreAccess.disableWriteAccess(true);
        
        readOnlyRunning = true;
        readOnlyRebindCount.set(0);

        try {
            rebind(null, null, mode);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
        
        Callable<Task<?>> taskFactory = new Callable<Task<?>>() {
            @Override public Task<Void> call() {
                return Tasks.<Void>builder().dynamic(false).displayName("rebind (periodic run").body(new Callable<Void>() {
                    public Void call() {
                        try {
                            rebind(null, null, mode);
                            return null;
                        } catch (RuntimeInterruptedException e) {
                            LOG.debug("Interrupted rebinding (re-interrupting): "+e);
                            if (LOG.isTraceEnabled())
                                LOG.trace("Interrupted rebinding (re-interrupting), details: "+e, e);
                            Thread.currentThread().interrupt();
                            return null;
                        } catch (Exception e) {
                            // Don't rethrow: the behaviour of executionManager is different from a scheduledExecutorService,
                            // if we throw an exception, then our task will never get executed again
                            if (!readOnlyRunning) {
                                LOG.debug("Problem rebinding (read-only running has probably just been turned off): "+e);
                                if (LOG.isTraceEnabled()) {
                                    LOG.trace("Problem rebinding (read-only running has probably just been turned off), details: "+e, e);
                                }
                            } else {
                                LOG.error("Problem rebinding: "+Exceptions.collapseText(e), e);
                            }
                            return null;
                        } catch (Throwable t) {
                            LOG.warn("Problem rebinding (rethrowing)", t);
                            throw Exceptions.propagate(t);
                        }
                    }}).build();
            }
        };
        readOnlyTask = (ScheduledTask) managementContext.getServerExecutionContext().submit(
            new ScheduledTask(MutableMap.of("displayName", "Periodic read-only rebind"), taskFactory).period(periodicPersistPeriod));
    }
    
    @Override
    public void stopReadOnly() {
        readOnlyRunning = false;
        if (readOnlyTask!=null) {
            LOG.debug("Stopping read-only rebinding ("+this+"), mgmt "+managementContext.getManagementNodeId());
            readOnlyTask.cancel(true);
            readOnlyTask.blockUntilEnded();
            boolean reallyEnded = Tasks.blockUntilInternalTasksEnded(readOnlyTask, Duration.TEN_SECONDS);
            if (!reallyEnded) {
                LOG.warn("Rebind (read-only) tasks took too long to die after interrupt (ignoring): "+readOnlyTask);
            }
            readOnlyTask = null;
            LOG.debug("Stopped read-only rebinding ("+this+"), mgmt "+managementContext.getManagementNodeId());
        }
    }
    
    @Override
    public void start() {
        ManagementNodeState target = getRebindMode();
        if (target==ManagementNodeState.HOT_STANDBY || target==ManagementNodeState.HOT_BACKUP) {
            startReadOnly(target);
        } else if (target==ManagementNodeState.MASTER) {
            startPersistence();
        } else {
            LOG.warn("Nothing to start in "+this+" when HA mode is "+target);
        }
    }

    @Override
    public void stop() {
        stopReadOnly();
        stopPersistence();
        if (persistenceStoreAccess != null) persistenceStoreAccess.stop(true);
    }
    
        
    public void rebindPartialActive(CompoundTransformer transformer, Iterator<BrooklynObject> objectsToRebind) {
        final ClassLoader classLoader = 
            managementContext.getCatalogClassLoader();
        // TODO we might want different exception handling for partials;
        // failure at various points should leave proxies in a sensible state,
        // either pointing at old or at new, though this is relatively untested,
        // and some things e.g. policies might not be properly started
        final RebindExceptionHandler exceptionHandler = 
            RebindExceptionHandlerImpl.builder()
                .danglingRefFailureMode(danglingRefFailureMode)
                .danglingRefQuorumRequiredHealthy(danglingRefsQuorumRequiredHealthy)
                .rebindFailureMode(rebindFailureMode)
                .addConfigFailureMode(addConfigFailureMode)
                .addPolicyFailureMode(addPolicyFailureMode)
                .loadPolicyFailureMode(loadPolicyFailureMode)
                .build();
        final ManagementNodeState mode = getRebindMode();

        ActivePartialRebindIteration iteration = new ActivePartialRebindIteration(this, mode, classLoader, exceptionHandler,
            rebindActive, readOnlyRebindCount, rebindMetrics, persistenceStoreAccess);

        iteration.setObjectIterator(Iterators.transform(objectsToRebind,
            new Function<BrooklynObject,BrooklynObject>() {
                @Override
                public BrooklynObject apply(BrooklynObject obj) {
                    // entities must be deproxied
                    if (obj instanceof Entity) obj = Entities.deproxy((Entity)obj);
                    return obj;
                }
            }));
        if (transformer!=null) iteration.applyTransformer(transformer);
        iteration.run();
    }
    
    public void rebindPartialActive(CompoundTransformer transformer, String ...objectsToRebindIds) {
        List<BrooklynObject> objectsToRebind = MutableList.of();
        for (String objectId: objectsToRebindIds) {
            BrooklynObject obj = managementContext.lookup(objectId);
            objectsToRebind.add(obj);
        }
        rebindPartialActive(transformer, objectsToRebind.iterator());
    }
    
    protected ManagementNodeState getRebindMode() {
        if (managementContext==null) throw new IllegalStateException("Invalid "+this+": no management context");
        if (!(managementContext.getHighAvailabilityManager() instanceof HighAvailabilityManagerImpl))
            throw new IllegalStateException("Invalid "+this+": unknown HA manager type "+managementContext.getHighAvailabilityManager());
        ManagementNodeState target = ((HighAvailabilityManagerImpl)managementContext.getHighAvailabilityManager()).getTransitionTargetNodeState();
        return target;
    }
    
    @Override
    @VisibleForTesting
    public void waitForPendingComplete(Duration timeout, boolean canTrigger) throws InterruptedException, TimeoutException {
        if (persistenceStoreAccess == null || !persistenceRunning) return;
        persistenceRealChangeListener.waitForPendingComplete(timeout, canTrigger);
        persistenceStoreAccess.waitForWritesCompleted(timeout);
    }
    @Override
    @VisibleForTesting
    public void forcePersistNow() {
        forcePersistNow(false, null);
    }
    @Override
    @VisibleForTesting
    public void forcePersistNow(boolean full, PersistenceExceptionHandler exceptionHandler) {
        if (full) {
            BrooklynMementoRawData memento = BrooklynPersistenceUtils.newStateMemento(managementContext, MementoCopyMode.LOCAL);
            if (exceptionHandler==null) {
                exceptionHandler = persistenceRealChangeListener.getExceptionHandler();
            }
            persistenceStoreAccess.checkpoint(memento, exceptionHandler);
        } else {
            if (persistenceRealChangeListener != null && !persistenceRealChangeListener.persistNowSafely()) {
                throw new IllegalStateException("Forced persistence failed; see logs fore more detail");
            }
        }
    }
    
    @Override
    public ChangeListener getChangeListener() {
        return persistencePublicChangeListener;
    }
    
    @Override
    public List<Application> rebind() {
        return rebind(null, null, null);
    }
    
    @Override
    public List<Application> rebind(final ClassLoader classLoader) {
        return rebind(classLoader, null, null);
    }

    @Override
    public List<Application> rebind(final ClassLoader classLoader, final RebindExceptionHandler exceptionHandler) {
        return rebind(classLoader, exceptionHandler, null);
    }
    
    @Override
    public List<Application> rebind(ClassLoader classLoaderO, RebindExceptionHandler exceptionHandlerO, ManagementNodeState modeO) {
        final ClassLoader classLoader = classLoaderO!=null ? classLoaderO :
            managementContext.getCatalogClassLoader();
        final RebindExceptionHandler exceptionHandler = exceptionHandlerO!=null ? exceptionHandlerO :
            RebindExceptionHandlerImpl.builder()
                .danglingRefFailureMode(danglingRefFailureMode)
                .danglingRefQuorumRequiredHealthy(danglingRefsQuorumRequiredHealthy)
                .rebindFailureMode(rebindFailureMode)
                .addConfigFailureMode(addConfigFailureMode)
                .addPolicyFailureMode(addPolicyFailureMode)
                .loadPolicyFailureMode(loadPolicyFailureMode)
                .build();
        final ManagementNodeState mode = modeO!=null ? modeO : getRebindMode();
        
        if (mode!=ManagementNodeState.MASTER && mode!=ManagementNodeState.HOT_STANDBY && mode!=ManagementNodeState.HOT_BACKUP)
            throw new IllegalStateException("Must be either master or hot standby/backup to rebind (mode "+mode+")");

        ExecutionContext ec = BasicExecutionContext.getCurrentExecutionContext();
        if (ec == null) {
            ec = managementContext.getServerExecutionContext();
            Task<List<Application>> task = ec.submit(new Callable<List<Application>>() {
                @Override public List<Application> call() throws Exception {
                    return rebindImpl(classLoader, exceptionHandler, mode);
                }});
            try {
                return task.get();
            } catch (Exception e) {
                throw Exceptions.propagate(e);
            }
        } else {
            return rebindImpl(classLoader, exceptionHandler, mode);
        }
    }
    
    @Override
    public BrooklynMementoRawData retrieveMementoRawData() {
        RebindExceptionHandler exceptionHandler = RebindExceptionHandlerImpl.builder()
                .danglingRefFailureMode(danglingRefFailureMode)
                .rebindFailureMode(rebindFailureMode)
                .addConfigFailureMode(addConfigFailureMode)
                .addPolicyFailureMode(addPolicyFailureMode)
                .loadPolicyFailureMode(loadPolicyFailureMode)
                .build();
        
        return loadMementoRawData(exceptionHandler);
    }

    /**
     * Uses the persister to retrieve (and thus deserialize) the memento.
     * 
     * In so doing, it instantiates the entities + locations, registering them with the rebindContext.
     */
    protected BrooklynMementoRawData loadMementoRawData(final RebindExceptionHandler exceptionHandler) {
        try {
            if (persistenceStoreAccess==null) {
                throw new IllegalStateException("Persistence not configured; cannot load memento data from persistent backing store");
            }
            if (!(persistenceStoreAccess instanceof BrooklynMementoPersisterToObjectStore)) {
                throw new IllegalStateException("Cannot load raw memento with persister "+persistenceStoreAccess);
            }
            
            return ((BrooklynMementoPersisterToObjectStore)persistenceStoreAccess).loadMementoRawData(exceptionHandler);
            
        } catch (RuntimeException e) {
            throw exceptionHandler.onFailed(e);
        }
    }
    
    protected List<Application> rebindImpl(final ClassLoader classLoader, final RebindExceptionHandler exceptionHandler, ManagementNodeState mode) {
        RebindIteration iteration = new InitialFullRebindIteration(this, mode, classLoader, exceptionHandler,
            rebindActive, readOnlyRebindCount, rebindMetrics, persistenceStoreAccess);
        
        iteration.run();
        
        if (firstRebindAppCount==null) {
            firstRebindAppCount = iteration.getApplications().size();
            firstRebindEntityCount = iteration.getRebindContext().getEntities().size();
            firstRebindItemCount = iteration.getRebindContext().getAllBrooklynObjects().size();
        }
        isAwaitingInitialRebind = false;

        return iteration.getApplications();
    }

    /**
     * Sorts the map of nodes, so that a node's parent is guaranteed to come before that node
     * (unless the parent is missing).
     * 
     * Relies on ordering guarantees of returned map (i.e. LinkedHashMap, which guarantees insertion order 
     * even if a key is re-inserted into the map).
     * 
     * TODO Inefficient implementation!
     */
    @VisibleForTesting
    static <T extends TreeNode> Map<String, T> sortParentFirst(Map<String, T> nodes) {
        Map<String, T> result = Maps.newLinkedHashMap();
        for (T node : nodes.values()) {
            List<T> tempchain = Lists.newLinkedList();
            
            T nodeinchain = node;
            while (nodeinchain != null) {
                tempchain.add(0, nodeinchain);
                nodeinchain = (nodeinchain.getParent() == null) ? null : nodes.get(nodeinchain.getParent());
            }
            for (T n : tempchain) {
                result.put(n.getId(), n);
            }
        }
        return result;
    }

    public boolean isAwaitingInitialRebind() {
        return isAwaitingInitialRebind;
    }

    public void setAwaitingInitialRebind(boolean isAwaitingInitialRebind) {
        this.isAwaitingInitialRebind = isAwaitingInitialRebind;
    }
    
    /**
     * Wraps a ChangeListener, to log and never propagate any exceptions that it throws.
     * 
     * Catches Throwable, because really don't want a problem to propagate up to user code,
     * to cause business-level operations to fail. For example, if there is a linkage error
     * due to some problem in the serialization dependencies then just log it. For things
     * more severe (e.g. OutOfMemoryError) then the catch+log means we'll report that we
     * failed to persist, and we'd expect other threads to throw the OutOfMemoryError so
     * we shouldn't lose anything.
     */
    private static class SafeChangeListener implements ChangeListener {
        private final ChangeListener delegate;
        
        public SafeChangeListener(ChangeListener delegate) {
            this.delegate = delegate;
        }
        
        @Override
        public void onManaged(BrooklynObject instance) {
            try {
                delegate.onManaged(instance);
            } catch (Throwable t) {
                LOG.error("Error persisting mememento onManaged("+instance+"); continuing.", t);
            }
        }

        @Override
        public void onChanged(BrooklynObject instance) {
            try {
                delegate.onChanged(instance);
            } catch (Throwable t) {
                LOG.error("Error persisting mememento onChanged("+instance+"); continuing.", t);
            }
        }
        
        @Override
        public void onUnmanaged(BrooklynObject instance) {
            try {
                delegate.onUnmanaged(instance);
            } catch (Throwable t) {
                LOG.error("Error persisting mememento onUnmanaged("+instance+"); continuing.", t);
            }
        }
    }

    public int getReadOnlyRebindCount() {
        return readOnlyRebindCount.get();
    }
    
    @Override
    public Map<String, Object> getMetrics() {
        Map<String,Object> result = MutableMap.of();

        result.put("rebind", rebindMetrics.asMap());
        result.put("persist", persistMetrics.asMap());
        
        if (readOnlyRebindCount.get()>=0)
            result.put("rebindReadOnlyCount", readOnlyRebindCount);
        
        // include first rebind counts, so we know whether we rebinded or not
        result.put("firstRebindCounts", MutableMap.of(
            "applications", firstRebindAppCount,
            "entities", firstRebindEntityCount,
            "allItems", firstRebindItemCount));
        
        return result;
    }

    @Override
    public String toString() {
        return super.toString()+"[mgmt="+managementContext.getManagementNodeId()+"]";
    }

}
