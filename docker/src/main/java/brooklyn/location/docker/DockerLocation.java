/*
 * Copyright 2014 by Cloudsoft Corporation Limited
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.container.DockerAttributes;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.docker.strategy.DockerAwarePlacementStrategy;
import brooklyn.location.docker.strategy.DockerAwareProvisioningStrategy;
import brooklyn.location.dynamic.DynamicLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.mutex.MutexSupport;
import brooklyn.util.mutex.WithMutexes;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.base.Optional;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

public class DockerLocation extends AbstractLocation implements DockerVirtualLocation, MachineProvisioningLocation<MachineLocation>,
        DynamicLocation<DockerInfrastructure, DockerLocation>, WithMutexes, Closeable {

    /** serialVersionUID */
	private static final long serialVersionUID = -4562281299895377963L;

	private static final Logger LOG = LoggerFactory.getLogger(DockerLocation.class);

    public static final String DOCKER_HOST_MUTEX = "dockerhost";

    @SetFromFlag("mutex")
    private transient WithMutexes mutexSupport = new MutexSupport();

    @SetFromFlag("owner")
    private DockerInfrastructure infrastructure;

    @SetFromFlag("strategies")
    private List<DockerAwarePlacementStrategy> strategies;

    @SetFromFlag("provisioner")
    private MachineProvisioningLocation<SshMachineLocation> provisioner;

    /* Mappings for provisioned locations */

    @SetFromFlag("machines")
    private final Multimap<SshMachineLocation, String> machines = HashMultimap.create();

    @SetFromFlag("containers")
    private final Map<String, DockerHostLocation> containers = Maps.newHashMap();

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
        acquireMutex(DOCKER_HOST_MUTEX, "Obtaining Docker host");
        try {
            // Check context for entity being deployed
            Object context = flags.get(LocationConfigKeys.CALLER_CONTEXT.getName());
            if (context != null && !(context instanceof Entity)) {
                throw new IllegalStateException("Invalid location context: " + context);
            }
            Entity entity = (Entity) context;

            // Get the available hosts based on placement strategies
            List<DockerHostLocation> available = getDockerHostLocations();
            for (DockerAwarePlacementStrategy strategy : strategies) {
                available = strategy.filterLocations(available, entity);
            }
            List<DockerAwarePlacementStrategy> entityStrategies = entity.getConfig(DockerAttributes.PLACEMENT_STRATEGIES);
            if (entityStrategies != null && entityStrategies.size() > 0) {
                for (DockerAwarePlacementStrategy strategy : entityStrategies) {
                    available = strategy.filterLocations(available, entity);
                }
            }

            // Use the docker strategy to add a new host
            DockerHostLocation machine = null;
            DockerHost dockerHost = null;
            if (available.size() > 0) {
                machine = available.get(0);
                dockerHost = machine.getOwner();
            } else {
                Iterable<DockerAwareProvisioningStrategy> provisioningStrategies = Iterables.filter(Iterables.concat(strategies,  entityStrategies), DockerAwareProvisioningStrategy.class);
                for (DockerAwareProvisioningStrategy strategy : provisioningStrategies) {
                    flags = strategy.apply((Map<String,Object>) flags);
                }

                LOG.info("Provisioning new host with flags: {}", flags);
                SshMachineLocation provisioned = getProvisioner().obtain(flags);
                Entity added = getDockerInfrastructure().getDockerHostCluster().addNode(provisioned, MutableMap.of());
                dockerHost = (DockerHost) added;
                machine = dockerHost.getDynamicLocation();
            }

            // Now wait until the host has started up
            Entities.waitForServiceUp(dockerHost);

            // Obtain a new Docker container location, save and return it
            if (LOG.isDebugEnabled()) {
                LOG.debug("Obtain a new container from {} for {}", machine, entity);
            }
            Map<?,?> hostFlags = MutableMap.copyOf(flags);
            DockerContainerLocation container = machine.obtain(hostFlags);
            machines.put(machine.getMachine(), container.getId());
            containers.put(container.getId(), machine);

            return container;
        } finally {
            releaseMutex(DOCKER_HOST_MUTEX);
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
        acquireMutex(DOCKER_HOST_MUTEX, "Releasing Docker host " + machine);
        try {
            String id = machine.getId();
            DockerHostLocation host = containers.remove(id);
            if (host == null) {
                throw new IllegalArgumentException("Request to release "+machine+", but this machine is not currently allocated");
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Request to remove container mapping {} to {}", host, id);
            }
            host.release((DockerContainerLocation) machine);
            if (machines.remove(host.getMachine(), id)) {
                if (machines.get(host.getMachine()).isEmpty()) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Empty Docker host: {}", host);
                    }
                    if (getOwner().getConfig(DockerInfrastructure.REMOVE_EMPTY_DOCKER_HOSTS)) {
                        LOG.info("Removing empty Docker host: {}", host);
                        remove(host);
                    }
                }
            } else {
                throw new IllegalArgumentException("Request to release "+machine+", but container mapping not found");
            }
        } finally {
            releaseMutex(DOCKER_HOST_MUTEX);
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

    @Override
    public void acquireMutex(String mutexId, String description) {
        try {
            mutexSupport.acquireMutex(mutexId, description);
        } catch (InterruptedException ie) {
            throw Exceptions.propagate(ie);
        }
    }

    @Override
    public boolean tryAcquireMutex(String mutexId, String description) {
        return mutexSupport.tryAcquireMutex(mutexId, description);
    }

    @Override
    public void releaseMutex(String mutexId) {
        mutexSupport.releaseMutex(mutexId);
    }

    @Override
    public boolean hasMutex(String mutexId) {
        return mutexSupport.hasMutex(mutexId);
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
