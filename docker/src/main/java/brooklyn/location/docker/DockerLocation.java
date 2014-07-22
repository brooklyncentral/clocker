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
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.group.DynamicCluster.NodePlacementStrategy;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.affinity.AffinityRuleExtension;
import brooklyn.location.affinity.DockerAffinityRuleStrategy;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.cloud.AvailabilityZoneExtension;
import brooklyn.location.docker.strategy.DepthFirstPlacementStrategy;
import brooklyn.location.dynamic.DynamicLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.mutex.MutexSupport;
import brooklyn.util.mutex.WithMutexes;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

public class DockerLocation extends AbstractLocation implements DockerVirtualLocation, MachineProvisioningLocation<MachineLocation>,
        DynamicLocation<DockerInfrastructure, DockerLocation>, WithMutexes, Closeable {

    /** serialVersionUID */
	private static final long serialVersionUID = -4562281299895377963L;

	private static final Logger LOG = LoggerFactory.getLogger(DockerLocation.class);

    public static final String DOCKER_HOST_MUTEX = "dockerhost";

    @SetFromFlag("mutex")
    private transient WithMutexes mutexSupport;

    @SetFromFlag("owner")
    private DockerInfrastructure infrastructure;

    @SetFromFlag("strategy")
    private NodePlacementStrategy strategy;

    @SetFromFlag("provisioner")
    private MachineProvisioningLocation<SshMachineLocation> provisioner;

    /* Mappings for provisioned locations */

    private final Multimap<SshMachineLocation, String> machines = HashMultimap.create();
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

    @Override
    public void init() {
        super.init();

        addExtension(AvailabilityZoneExtension.class, new DockerHostExtension(getManagementContext(), this));
        addExtension(AffinityRuleExtension.class, new DockerAffinityRuleStrategy(getManagementContext(), this));
    }

    public MachineProvisioningLocation<SshMachineLocation> getProvisioner() {
        return provisioner;
    }

    @Override
    public void configure(Map properties) {
        if (strategy == null) {
            strategy = new DepthFirstPlacementStrategy();
        }
        if (mutexSupport == null) {
            mutexSupport = new MutexSupport();
        }
        super.configure(properties);
    }

    public MachineLocation obtain() throws NoMachinesAvailableException {
        return obtain(Maps.<String,Object>newLinkedHashMap());
    }

    @Override
    public MachineLocation obtain(Map<?,?> flags) throws NoMachinesAvailableException {
        try {
            acquireMutex(DOCKER_HOST_MUTEX, "Obtaining Docker host");

            // Check context for entity being deployed
            Object context = flags.get(LocationConfigKeys.CALLER_CONTEXT.getName());
            if (context != null && !(context instanceof Entity)) {
                throw new IllegalStateException("Invalid location context: " + context);
            }
            Entity entity = (Entity) context;

            // Get the available hosts based on affinity rules
             List<Location> dockerHosts = getExtension(AffinityRuleExtension.class).filterLocations(entity);

            // Use the docker strategy to add a new host
            DockerHostLocation machine = null;
            DockerHost dockerHost = null;
            if (dockerHosts != null && dockerHosts.size() > 0) {
                List<Location> added = strategy.locationsForAdditions(null, dockerHosts, 1);
                machine = (DockerHostLocation) Iterables.getOnlyElement(added);
                dockerHost = machine.getOwner();
            } else {
                Collection<Entity> added = getDockerInfrastructure().getDockerHostCluster().resizeByDelta(1);
                dockerHost = (DockerHost) Iterables.getOnlyElement(added);
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
        } catch (InterruptedException ie) {
            throw Exceptions.propagate(ie);
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
        try {
            acquireMutex(DOCKER_HOST_MUTEX, "Releasing Docker host " + machine);

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
        } catch (InterruptedException ie) {
            throw Exceptions.propagate(ie);
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
    public void acquireMutex(String mutexId, String description) throws InterruptedException {
        mutexSupport.acquireMutex(mutexId, description);
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
                .add("strategy", strategy);
    }

}
