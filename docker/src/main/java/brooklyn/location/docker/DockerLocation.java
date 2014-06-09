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
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.cloud.AvailabilityZoneExtension;
import brooklyn.location.docker.strategy.DepthFirstPlacementStrategy;
import brooklyn.location.docker.strategy.DockerAwarePlacementStrategy;
import brooklyn.location.dynamic.DynamicLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

public class DockerLocation extends AbstractLocation implements DockerVirtualLocation,
        MachineProvisioningLocation<MachineLocation>, DynamicLocation<DockerInfrastructure, DockerLocation> {

    private static final Logger LOG = LoggerFactory.getLogger(DockerLocation.class);

    @SetFromFlag("mutex")
    private Object mutex;

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
        if (strategy == null) {
            strategy = new DepthFirstPlacementStrategy();
        }
        addExtension(AvailabilityZoneExtension.class, new DockerHostExtension(getManagementContext(), this));
    }

    public MachineProvisioningLocation<SshMachineLocation> getProvisioner() {
        return provisioner;
    }

    @Override
    public void configure(Map properties) {
        if (mutex == null) {
            mutex = new Object[0];
        }
        super.configure(properties);
    }

    public MachineLocation obtain() throws NoMachinesAvailableException {
        return obtain(Maps.<String,Object>newLinkedHashMap());
    }

    @Override
    public MachineLocation obtain(Map<?,?> flags) throws NoMachinesAvailableException {
        synchronized (mutex) {
            // Check context for entity being deployed
            Object context = flags.get(LocationConfigKeys.CALLER_CONTEXT.getName());
            if (context != null && !(context instanceof Entity)) {
                throw new IllegalStateException("Invalid location context: " + context);
            }
            Entity entity = (Entity) context;

            // Use the docker strategy to add a new host
            List<Location> dockerHosts = getExtension(AvailabilityZoneExtension.class).getAllSubLocations();
            List<Location> added = strategy.locationsForAdditions(null, dockerHosts, 1);
            DockerHostLocation machine = (DockerHostLocation) Iterables.getOnlyElement(added);
            DockerHost dockerHost = machine.getOwner();

            // Now wait until the host has started up
            Entities.waitForServiceUp(dockerHost);

            // Obtain a new Docker container location, save and return it
            if (LOG.isDebugEnabled()) {
                LOG.debug("Obtain a new container from {} for {}", machine, entity);
            }
            DockerContainerLocation container = machine.obtain(MutableMap.of("entity", entity));
            machines.put(machine.getMachine(), container.getId());
            containers.put(container.getId(), machine);

            return container;
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
        synchronized (mutex) {
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
    public ToStringHelper string() {
        return super.string()
                .omitNullValues()
                .add("provisioner", provisioner)
                .add("infrastructure", infrastructure)
                .add("strategy", strategy);
    }

    @Override
    public DockerInfrastructure getOwner() {
        return infrastructure;
    }

}
