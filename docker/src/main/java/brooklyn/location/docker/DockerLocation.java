/*
 * Copyright 2014-2015 by Cloudsoft Corporation Limited
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
package brooklyn.location.docker;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.rebind.RebindContext;
import org.apache.brooklyn.api.entity.rebind.RebindSupport;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.api.location.NoMachinesAvailableException;
import org.apache.brooklyn.api.mementos.LocationMemento;
import org.apache.brooklyn.location.basic.AbstractLocation;
import org.apache.brooklyn.location.basic.LocationConfigKeys;
import org.apache.brooklyn.location.basic.SshMachineLocation;
import org.apache.brooklyn.location.dynamic.DynamicLocation;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityFunctions;
import brooklyn.entity.container.DockerAttributes;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.rebind.BasicLocationRebindSupport;
import brooklyn.location.docker.strategy.DockerAwarePlacementStrategy;
import brooklyn.location.docker.strategy.DockerAwareProvisioningStrategy;
import brooklyn.networking.location.NetworkProvisioningExtension;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.SetFromFlag;

public class DockerLocation extends AbstractLocation implements DockerVirtualLocation, MachineProvisioningLocation<MachineLocation>,
        DynamicLocation<DockerInfrastructure, DockerLocation>, Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(DockerLocation.class);

    @SetFromFlag("owner")
    private DockerInfrastructure infrastructure;

    @SetFromFlag("strategies")
    private List<DockerAwarePlacementStrategy> strategies;

    @SetFromFlag("provisioner")
    private MachineProvisioningLocation<SshMachineLocation> provisioner;

    @SetFromFlag("machines")
    private final SetMultimap<DockerHostLocation, String> containers = Multimaps.synchronizedSetMultimap(HashMultimap.<DockerHostLocation, String>create());

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

        // Get the available hosts based on placement strategies
        List<DockerHostLocation> available = getDockerHostLocations();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Placement for: {}", Iterables.toString(Iterables.transform(available, EntityFunctions.id())));
        }
        for (DockerAwarePlacementStrategy strategy : strategies) {
            available = strategy.filterLocations(available, entity);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Placement after {}: {}", strategy, Iterables.toString(Iterables.transform(available, EntityFunctions.id())));
            }
        }
        List<DockerAwarePlacementStrategy> entityStrategies = entity.config().get(DockerAttributes.PLACEMENT_STRATEGIES);
        if (entityStrategies != null && entityStrategies.size() > 0) {
            for (DockerAwarePlacementStrategy strategy : entityStrategies) {
                available = strategy.filterLocations(available, entity);
            }
        } else {
            entityStrategies = ImmutableList.of();
        }

        // Use the docker strategy to add a new host
        DockerHostLocation machine = null;
        DockerHost dockerHost = null;
        if (available.size() > 0) {
            machine = available.get(0);
            dockerHost = machine.getOwner();
        } else {
            // Get permission to create a new Docker host
            if (permit.tryAcquire()) {
                try {
                    Iterable<DockerAwareProvisioningStrategy> provisioningStrategies = Iterables.filter(Iterables.concat(strategies,  entityStrategies), DockerAwareProvisioningStrategy.class);
                    for (DockerAwareProvisioningStrategy strategy : provisioningStrategies) {
                        flags = strategy.apply((Map<String,Object>) flags);
                    }

                    LOG.info("Provisioning new host with flags: {}", flags);
                    SshMachineLocation provisioned = getProvisioner().obtain(flags);
                    Entity added = getDockerInfrastructure().getDockerHostCluster().addNode(provisioned, MutableMap.of());
                    dockerHost = (DockerHost) added;
                    machine = dockerHost.getDynamicLocation();
                    Entities.start(added, ImmutableList.of(provisioned));
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
        if (LOG.isDebugEnabled()) {
            LOG.debug("Obtain a new container from {} for {}", machine, entity);
        }
        Map<?,?> hostFlags = MutableMap.copyOf(flags);
        DockerContainerLocation container = machine.obtain(hostFlags);
        containers.put(machine, container.getId());
        return container;
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
        if (LOG.isDebugEnabled()) {
            LOG.debug("Request to remove container mapping {} to {}", host, id);
        }
        host.release((DockerContainerLocation) machine);
        if (containers.remove(host, id)) {
            if (containers.get(host).isEmpty()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Empty Docker host: {}", host);
                }

                // Remove hosts when it has no containers, except for the last one
                if (getOwner().config().get(DockerInfrastructure.REMOVE_EMPTY_DOCKER_HOSTS) && set.size() > 1) {
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
            LOG.info("Docker Host {} released", host.getDockerHostName());
        } else {
            LOG.warn("Docker Host {} not found for release", host.getDockerHostName());
        }

        // Now close and unmange the host
        try {
            machine.close();
            host.stop();
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

    public List<Entity> getDockerContainerList() {
        return infrastructure.getDockerContainerList();
    }

    public List<Entity> getDockerHostList() {
        return infrastructure.getDockerHostList();
    }

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
