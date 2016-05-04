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
package clocker.docker.location;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clocker.docker.entity.DockerHost;
import clocker.docker.entity.DockerInfrastructure;
import clocker.docker.entity.util.DockerAttributes;
import clocker.docker.location.strategy.DockerAwarePlacementStrategy;
import clocker.docker.networking.location.NetworkProvisioningExtension;
import clocker.docker.policy.ContainerHeadroomEnricher;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.LocationDefinition;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.api.location.NoMachinesAvailableException;
import org.apache.brooklyn.api.mgmt.rebind.RebindContext;
import org.apache.brooklyn.api.mgmt.rebind.RebindSupport;
import org.apache.brooklyn.api.mgmt.rebind.mementos.LocationMemento;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityFunctions;
import org.apache.brooklyn.core.location.AbstractLocation;
import org.apache.brooklyn.core.location.BasicLocationDefinition;
import org.apache.brooklyn.core.location.LocationConfigKeys;
import org.apache.brooklyn.core.location.dynamic.DynamicLocation;
import org.apache.brooklyn.core.mgmt.rebind.BasicLocationRebindSupport;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.policy.autoscaling.AutoScalerPolicy;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.core.mutex.WithMutexes;
import org.apache.brooklyn.util.exceptions.Exceptions;

public class DockerLocation extends AbstractLocation implements DockerVirtualLocation, MachineProvisioningLocation<MachineLocation>,
        DynamicLocation<DockerInfrastructure, DockerLocation>, Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(DockerLocation.class);

    public static final ConfigKey<String> LOCATION_NAME = ConfigKeys.newStringConfigKey("locationName");

    @SetFromFlag("strategies")
    private List<DockerAwarePlacementStrategy> strategies;

    @SetFromFlag("provisioner")
    private MachineProvisioningLocation<SshMachineLocation> provisioner;

    @SetFromFlag("machines")
    private final SetMultimap<DockerHostLocation, String> containers = Multimaps.synchronizedSetMultimap(HashMultimap.<DockerHostLocation, String>create());

    @SetFromFlag("locationRegistrationId")
    private String locationRegistrationId;

    private transient DockerInfrastructure infrastructure;
    private transient Semaphore permit = new Semaphore(1);

    public DockerLocation() {
        this(Maps.newLinkedHashMap());
    }

    public DockerLocation(Map properties) {
        super(properties);

        if (isLegacyConstruction()) {
            init();
        }
    }

    @Override
    public void init() {
        super.init();
        
        // TODO BasicLocationRebindsupport.addCustoms currently calls init() unfortunately!
        // Don't checkNotNull in that situation - it could be this location is orphaned!
        if (isRebinding()) {
            infrastructure = (DockerInfrastructure) getConfig(OWNER);
        } else {
            infrastructure = (DockerInfrastructure) checkNotNull(getConfig(OWNER), "owner");
        }
    }
    
    @Override
    public void rebind() {
        super.rebind();
        
        infrastructure = (DockerInfrastructure) getConfig(OWNER);
        
        if (infrastructure != null && getConfig(LOCATION_NAME) != null) {
            register();
        }
    }

    @Override
    public LocationDefinition register() {
        String locationName = checkNotNull(getConfig(LOCATION_NAME), "config %s", LOCATION_NAME.getName());

        LocationDefinition check = getManagementContext().getLocationRegistry().getDefinedLocationByName(locationName);
        if (check != null) {
            throw new IllegalStateException("Location " + locationName + " is already defined: " + check);
        }

        String locationSpec = String.format(DockerResolver.DOCKER_INFRASTRUCTURE_SPEC, getId()) + String.format(":(name=\"%s\")", locationName);

        LocationDefinition definition = new BasicLocationDefinition(locationName, locationSpec, ImmutableMap.<String, Object>of());
        getManagementContext().getLocationRegistry().updateDefinedLocation(definition);
        
        locationRegistrationId = definition.getId();
        requestPersist();
        
        return definition;
    }
    
    @Override
    public void deregister() {
        if (locationRegistrationId != null) {
            getManagementContext().getLocationRegistry().removeDefinedLocation(locationRegistrationId);
            locationRegistrationId = null;
            requestPersist();
        }
    }
    
    public MachineProvisioningLocation<SshMachineLocation> getProvisioner() {
        return provisioner;
    }

    protected List<DockerHostLocation> getDockerHostLocations() {
        List<Optional<DockerHostLocation>> result = Lists.newArrayList();
        for (Entity entity : getDockerHostList()) {
            DockerHost host = (DockerHost) entity;
            DockerHostLocation machine = host.getDynamicLocation();
            result.add(Optional.<DockerHostLocation>fromNullable(machine));
        }
        return ImmutableList.copyOf(Optional.presentInstances(result));
    }

    public MachineLocation obtain() throws NoMachinesAvailableException {
        return obtain(Maps.<String,Object>newLinkedHashMap());
    }

    @Override
    public MachineLocation obtain(Map<?,?> flags) throws NoMachinesAvailableException {
        // Check context for entity being deployed
        Object context = flags.get(LocationConfigKeys.CALLER_CONTEXT.getName());
        if (context != null && !(context instanceof Entity)) {
            throw new IllegalStateException("Invalid location context: " + context);
        }
        Entity entity = (Entity) context;

        // Get the available hosts
        List<DockerHostLocation> available = getDockerHostLocations();
        LOG.debug("Placement for: {}", Iterables.toString(Iterables.transform(available, EntityFunctions.id())));

        // Apply placement strategies
        List<DockerAwarePlacementStrategy> entityStrategies = entity.config().get(DockerAttributes.PLACEMENT_STRATEGIES);
        if (entityStrategies == null) entityStrategies = ImmutableList.of();
        for (DockerAwarePlacementStrategy strategy : Iterables.concat(strategies, entityStrategies)) {
            available = strategy.filterLocations(available, entity);
            LOG.debug("Placement after {}: {}", strategy, Iterables.toString(Iterables.transform(available, EntityFunctions.id())));
        }

        // Use the provisioning strategy to add a new host
        DockerHostLocation machine = null;
        DockerHost dockerHost = null;
        if (available.size() > 0) {
            machine = available.get(0);
            dockerHost = machine.getOwner();
        } else {
            // Get permission to create a new Docker host
            if (permit.tryAcquire()) {
                try {
                    // Determine if headroom scaling policy is being used and suspend
                    Integer headroom = getOwner().config().get(ContainerHeadroomEnricher.CONTAINER_HEADROOM);
                    Double headroomPercent = getOwner().config().get(ContainerHeadroomEnricher.CONTAINER_HEADROOM_PERCENTAGE);
                    boolean headroomSet = (headroom != null && headroom > 0) || (headroomPercent != null && headroomPercent > 0d);
                    Optional<Policy> policy = Iterables.tryFind(getOwner().getDockerHostCluster().policies(), Predicates.instanceOf(AutoScalerPolicy.class));
                    if (headroomSet && policy.isPresent()) policy.get().suspend();

                    try {
                        // Resize the host cluster
                        LOG.info("Provisioning new host");
                        Entity added = Iterables.getOnlyElement(getOwner().getDockerHostCluster().resizeByDelta(1));
                        dockerHost = (DockerHost) added;
                        machine = dockerHost.getDynamicLocation();

                        // Update autoscaler policy with new minimum size and resume
                        if (headroomSet && policy.isPresent()) {
                            int currentMin = policy.get().config().get(AutoScalerPolicy.MIN_POOL_SIZE);
                            LOG.info("Updating autoscaler policy ({}) setting {} to {}",
                                    new Object[] { policy.get(), AutoScalerPolicy.MIN_POOL_SIZE.getName(), currentMin + 1 });
                            policy.get().config().set(AutoScalerPolicy.MIN_POOL_SIZE, currentMin + 1);
                        }
                    } finally {
                        if (policy.isPresent()) policy.get().resume();
                    }
                } finally {
                    permit.release();
                }
            } else {
                // Wait until whoever has the permit releases it, and try again
                try {
                    permit.acquire();
                } catch (InterruptedException ie) {
                    Exceptions.propagate(ie);
                } finally {
                    permit.release();
                }
                return obtain(flags);
            }
        }

        // Now wait until the host has started up
        Entities.waitForServiceUp(dockerHost);

        // Obtain a new Docker container location, save and return it
        try {
            LOG.debug("Obtain a new container from {} for {}", machine, entity);
            Map<?,?> hostFlags = MutableMap.copyOf(flags);
            DockerContainerLocation container = machine.obtain(hostFlags);
            containers.put(machine, container.getId());
            return container;
        } finally {
            // Release any placement strategy locks
            for (DockerAwarePlacementStrategy strategy : Iterables.concat(strategies, entityStrategies)) {
                if (strategy instanceof WithMutexes) {
                    ((WithMutexes) strategy).releaseMutex(entity.getApplicationId());
                }
            }
        }
    }

    @Override
    public MachineProvisioningLocation<MachineLocation> newSubLocation(Map<?, ?> newFlags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void release(MachineLocation machine) {
        if (provisioner == null) {
            throw new IllegalStateException("No provisioner available to release "+machine);
        }
        String id = machine.getId();
        Set<DockerHostLocation> set = Multimaps.filterValues(containers, Predicates.equalTo(id)).keySet();
        if (set.isEmpty()) {
            throw new IllegalArgumentException("Request to release "+machine+", but this machine is not currently allocated");
        }
        DockerHostLocation host = Iterables.getOnlyElement(set);
        LOG.debug("Request to remove container mapping {} to {}", host, id);
        host.release((DockerContainerLocation) machine);
        if (containers.remove(host, id)) {
            if (containers.get(host).isEmpty()) {
                LOG.debug("Empty Docker host: {}", host);

                // Remove hosts when it has no containers, except for the last one
                if (infrastructure.config().get(DockerInfrastructure.REMOVE_EMPTY_DOCKER_HOSTS) && set.size() > 1) {
                    LOG.info("Removing empty Docker host: {}", host);
                    remove(host);
                }
            }
        } else {
            throw new IllegalArgumentException("Request to release "+machine+", but container mapping not found");
        }
    }

    protected void remove(DockerHostLocation machine) {
        LOG.info("Releasing {}", machine);
        DynamicCluster cluster = infrastructure.getDockerHostCluster();
        DockerHost host = machine.getOwner();
        if (cluster.removeMember(host)) {
            LOG.info("Docker Host {} released", host);
        } else {
            LOG.warn("Docker Host {} not found for release", host);
        }

        // TODO update autoscaler policy pool size

        // Now close and unmange the host
        try {
            host.stop();
            machine.close();
        } catch (Exception e) {
            LOG.warn("Error stopping host: " + host, e);
            Exceptions.propagateIfFatal(e);
        } finally {
            Entities.unmanage(host);
        }
    }

    @Override
    public Map<String,Object> getProvisioningFlags(Collection<String> tags) {
        return Maps.newLinkedHashMap();
    }

    @Override
    public DockerInfrastructure getOwner() {
        return infrastructure;
    }

    @Override
    public List<Entity> getDockerContainerList() {
        return infrastructure.getDockerContainerList();
    }

    @Override
    public List<Entity> getDockerHostList() {
        return infrastructure.getDockerHostList();
    }

    @Override
    public DockerInfrastructure getDockerInfrastructure() {
        return infrastructure;
    }

    @Override
    public void close() throws IOException {
        LOG.info("Close called on Docker infrastructure: {}", this);
    }

    // FIXME this should be supported in core Brooklyn for all extension tyoes
    @Override
    public RebindSupport<LocationMemento> getRebindSupport() {
        NetworkProvisioningExtension networkProvisioningExtension = null;
        if (hasExtension(NetworkProvisioningExtension.class)) {
            networkProvisioningExtension = getExtension(NetworkProvisioningExtension.class);
        }
        final Optional<NetworkProvisioningExtension> extension = Optional.fromNullable(networkProvisioningExtension);
        return new BasicLocationRebindSupport(this) {
            @Override public LocationMemento getMemento() {
                return getMementoWithProperties(MutableMap.<String, Object>of("networkProvisioningExtension", extension));
            }
            @Override
            protected void doReconstruct(RebindContext rebindContext, LocationMemento memento) {
                super.doReconstruct(rebindContext, memento);
                Optional<NetworkProvisioningExtension> extension = (Optional<NetworkProvisioningExtension>) memento.getCustomField("networkProvisioningExtension");
                if (extension.isPresent()) {
                    addExtension(NetworkProvisioningExtension.class, extension.get());
                }
            }
        };
    }

    @Override
    public ToStringHelper string() {
        return super.string()
                .omitNullValues()
                .add("provisioner", provisioner)
                .add("infrastructure", infrastructure)
                .add("strategies", strategies);
    }
}
