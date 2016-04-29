/*
 * Copyright 2014-2016 by Cloudsoft Corporation Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package clocker.mesos.entity.framework;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clocker.mesos.entity.MesosCluster;
import clocker.mesos.entity.task.MesosTask;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityFunctions;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.feed.ConfigToAttributes;
import org.apache.brooklyn.entity.group.BasicGroup;
import org.apache.brooklyn.entity.stock.BasicStartableImpl;
import org.apache.brooklyn.entity.stock.DelegateEntity;
import org.apache.brooklyn.feed.http.HttpFeed;
import org.apache.brooklyn.feed.http.HttpPollConfig;
import org.apache.brooklyn.feed.http.HttpValueFunctions;
import org.apache.brooklyn.feed.http.JsonFunctions;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.QuorumCheck.QuorumChecks;
import org.apache.brooklyn.util.guava.Functionals;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;

/**
 * Mesos frameworks shared implementation.
 */
public class MesosFrameworkImpl extends BasicStartableImpl implements MesosFramework {

    // TODO Rebind is broken: the connectSensors doesn't create a persisted feed
    // (and its use of an annonymous inner class makes that hard). So when we restart
    // Brooklyn, we don't rescan.
    //
    // Same for MarathonTaskImpl.connectSensors: that isn't called on connectSensors either.
    
    private static final Logger LOG = LoggerFactory.getLogger(MesosFramework.class);

    private transient HttpFeed taskScan;

    @Override
    public void init() {
        LOG.info("Connecting to framework id: {}", getId());
        super.init();

        ConfigToAttributes.apply(this);

        Group tasks = addChild(EntitySpec.create(BasicGroup.class)
                .configure(BasicGroup.RUNNING_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty())
                .configure(BasicGroup.UP_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty())
                .displayName("Framework Tasks"));
        sensors().set(FRAMEWORK_TASKS, tasks);

        sensors().set(Attributes.MAIN_URI, URI.create(config().get(FRAMEWORK_URL)));
    }

    @Override
    public void rebind() {
        super.rebind();

        if (taskScan == null) {
            // The feed calls scanTasks, which modifies other entities.That is dangerous during 
            // rebind, because the rebind-thread may concurrently (or subsequently) be initialising 
            // those other entities. Similar code (e.g. DynamicMultiGroup) has caused rebind of 
            // of those other entities to subsequently fail.
            //
            // Normal best-practice would be to use {@code feeds().addFeed(feed)} so that the entity
            // automatically persists its feed, but this is no ordinary feed! Therefore safer (for 
            // now) to do it this way.
            
            getExecutionContext().execute(new Runnable() {
                @Override public void run() {
                    LOG.debug("Deferring scanner for {} until management context initialisation complete", MesosFrameworkImpl.this);
                    while (!isRebindComplete()) {
                        Time.sleep(100); // avoid thrashing
                    }
                    LOG.debug("Connecting scanner for {}", MesosFrameworkImpl.this);
                    connectSensors();
                }
                private boolean isRebindComplete() {
                    return !getManagementContext().getRebindManager().isAwaitingInitialRebind();
                }});
        }
    }

    @Override
    public void start(Collection<? extends Location> locs) {
        clearLocations();

        ServiceStateLogic.setExpectedState(this, Lifecycle.STARTING);

        connectSensors();

        sensors().set(Attributes.SERVICE_UP, Boolean.TRUE);
        ServiceStateLogic.setExpectedState(this, Lifecycle.RUNNING);
    }

    @Override
    public void stop() {
        disconnectSensors();

        sensors().set(SERVICE_UP, Boolean.FALSE);
    }

    @Override
    public void connectSensors() {
        Duration scanInterval = config().get(MesosCluster.SCAN_INTERVAL);
        HttpFeed.Builder taskScanBuilder = HttpFeed.builder()
                .entity(this)
                .period(scanInterval)
                .baseUri(config().get(MesosCluster.MESOS_URL))
                .credentialsIfNotNull(config().get(MesosCluster.MESOS_USERNAME), config().get(MesosCluster.MESOS_PASSWORD))
                .poll(HttpPollConfig.forSensor(MESOS_TASK_LIST)
                        .description("Scan Tasks")
                        .suburl("/master/state.json")
                        .onSuccess(Functionals.chain(HttpValueFunctions.jsonContents(), JsonFunctions.walk("frameworks"), new Function<JsonElement, List<String>>() {
                            @Override
                            public List<String> apply(JsonElement frameworks) {
                                return scanTasks(frameworks.getAsJsonArray());
                            }
                        }))
                        .onFailureOrException(Functions.<List<String>>constant(null)));
        taskScan = taskScanBuilder.build();
    }

    @Override
    public void deleteUnmanagedTasks() {
        for (Entity member : getTaskCluster().getMembers()) {
            if (member instanceof MesosTask && Boolean.FALSE.equals(member.config().get(MesosTask.MANAGED))) {
                // TODO Presumably `unmanage(member)` would be enough, but leaving like this
                // because that is what Andrew previously used!
                getTaskCluster().removeMember(member);
                getTaskCluster().removeChild(member);
                Entities.unmanage(member);
            }
        }
    }
    
    public List<String> scanTasks(JsonArray frameworks) {
        String frameworkId = sensors().get(FRAMEWORK_ID);
        Entity mesosCluster = sensors().get(MESOS_CLUSTER);
        LOG.debug("Periodic scanning of framework tasks: frameworkId={}, mesosCluster={}", frameworkId, mesosCluster);
        
        for (int i = 0; i < frameworks.size(); i++) {
            JsonObject framework = frameworks.get(i).getAsJsonObject();
            if (frameworkId.equals(framework.get("id").getAsString())) {
                JsonArray completed = framework.getAsJsonArray("completed_tasks");
                JsonArray tasks = framework.getAsJsonArray("tasks");
                sensors().set(MESOS_COMPLETED_TASKS, completed.size());
                sensors().set(MESOS_RUNNING_TASKS, tasks.size());

                List<String> taskNames = MutableList.<String>of();
                for (int j = 0; j < tasks.size(); j++) {
                    JsonObject json = tasks.get(j).getAsJsonObject();
                    String id = json.get("id").getAsString();
                    String name = json.get("name").getAsString();
                    String state = json.get("state").getAsString();

                    Optional<Entity> taskEntity = Iterables.tryFind(sensors().get(FRAMEWORK_TASKS).getMembers(),
                              Predicates.compose(Predicates.equalTo(id), EntityFunctions.attribute(MesosTask.TASK_ID)));
                    MesosTask task = null;
                    if (taskEntity.isPresent()) {
                        // Only interested in tasks for our own use of Marathon.
                        // Tasks that other people create we'll ignore.
                        task = (MesosTask) taskEntity.get();
                    }
                    if (task != null) {
                        taskNames.add(name);
                        task.sensors().set(MesosTask.TASK_ID, id);
                        task.sensors().set(MesosTask.TASK_STATE, state);
                    }
                }
                
                Set<String> taskNamesSet = Sets.newHashSet(taskNames);
                for (Entity member : ImmutableList.copyOf(getTaskCluster().getMembers())) {
                    final String name = member.sensors().get(MesosTask.TASK_NAME);
                    if (name != null) {
                        boolean found = taskNamesSet.contains(name);
                        if (found) continue;
                    }

                    // Stop and then remove the task as it is no longer running, unless ON_FIRE
                    //
                    // TODO Aled worries about this: if task has gone, then MarathonTask polling
                    // the task's URL will fail, setting the serviceUp to false, setting it 
                    // on-fire. What will ever remove the task on a graceful shutdown of a container?
                    // Or is that handled elsewhere?
                    Lifecycle state = member.sensors().get(Attributes.SERVICE_STATE_ACTUAL);
                    if (Lifecycle.ON_FIRE.equals(state) || Lifecycle.STARTING.equals(state)) {
                        continue;
                    } else if (Lifecycle.STOPPING.equals(state) || Lifecycle.STOPPED.equals(state)) {
                        getTaskCluster().removeMember(member);
                        getTaskCluster().removeChild(member);
                        Entities.unmanage(member);
                    } else {
                        ServiceStateLogic.setExpectedState(member, Lifecycle.STOPPING);
                    }
                }
                return taskNames;
            }
        }
        // not found
        return null;
    }

    @Override
    public void disconnectSensors() {
        if (taskScan  != null && taskScan.isActivated()) taskScan.destroy();
        taskScan = null;
    }

    @Override
    public MesosTask startTask(Map<String, Object> taskFlags) {
        throw new UnsupportedOperationException("Not implemented on this framework");
    }

    @Override
    public List<Class<? extends Entity>> getSupported() {
        return ImmutableList.of();
    }

    @Override
    public MesosCluster getMesosCluster() {
        return (MesosCluster) sensors().get(MESOS_CLUSTER);
    }

    @Override
    public Group getTaskCluster() {
        return sensors().get(FRAMEWORK_TASKS);
    }

    static {
        RendererHints.register(FRAMEWORK_URL, RendererHints.namedActionWithUrl());
        RendererHints.register(MESOS_CLUSTER, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(FRAMEWORK_TASKS, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
    }

}
