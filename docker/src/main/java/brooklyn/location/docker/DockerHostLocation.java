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

import java.net.InetAddress;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import brooklyn.entity.Entity;
import brooklyn.entity.container.docker.DockerAttributes;
import brooklyn.entity.container.docker.DockerContainer;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.location.MachineDetails;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.cloud.AvailabilityZoneExtension;
import brooklyn.location.dynamic.DynamicLocation;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.SetFromFlag;

public class DockerHostLocation extends AbstractLocation implements MachineLocation,
        MachineProvisioningLocation<DockerContainerLocation>, DockerVirtualLocation,
        DynamicLocation<DockerHost, DockerHostLocation> {

    private static final Logger LOG = LoggerFactory.getLogger(DockerHostLocation.class);

    @SetFromFlag("machine")
    private SshMachineLocation machine;

    @SetFromFlag("jcloudsLocation")
    private JcloudsLocation jcloudsLocation;

    @SetFromFlag("owner")
    private DockerHost dockerHost;

    public DockerHostLocation() {
        this(Maps.newLinkedHashMap());
    }

    public DockerHostLocation(Map properties) {
        super(properties);

        if (isLegacyConstruction()) {
            init();
        }
    }

    @Override
    public void init() {
        super.init();
        addExtension(AvailabilityZoneExtension.class, new DockerContainerExtension(getManagementContext(), this));
    }

    public DockerContainerLocation obtain() throws NoMachinesAvailableException {
        return obtain(Maps.<String,Object>newLinkedHashMap());
    }

    @Override
    public DockerContainerLocation obtain(Map<?,?> flags) throws NoMachinesAvailableException {
        Integer maxSize = dockerHost.getConfig(DockerHost.DOCKER_CONTAINER_CLUSTER_MAX_SIZE);
        Integer currentSize = dockerHost.getAttribute(DockerAttributes.DOCKER_CONTAINER_COUNT);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Docker host {}: {} containers, max {}", new Object[] { dockerHost.getDockerHostName(),
                    currentSize, maxSize });
        }

        // also try to satisfy the affinty rules etc.
        if (currentSize != null && currentSize >= maxSize) {
            throw new NoMachinesAvailableException(String.format("Limit of %d containers reached at %s", maxSize, dockerHost.getDockerHostName()));
        }

        // increase size of Docker container cluster
        DynamicCluster cluster = dockerHost.getDockerContainerCluster();
        //Optional<Entity> added = cluster.growByOne(machine, flags);
        Optional<Entity> added = cluster.addInSingleLocation(jcloudsLocation, flags);
        if (!added.isPresent()) {
            throw new NoMachinesAvailableException(String.format("Failed to create containers. Limit reached at %s", dockerHost.getDockerHostName()));
        }
        DockerContainer dockerContainer = (DockerContainer) added.get();
        return dockerContainer.getDynamicLocation();
    }

    @Override
    public void release(DockerContainerLocation machine) {
        LOG.info("Docker Host {}: releasing {}", new Object[] { dockerHost.getDockerHostName(), machine });
        DynamicCluster cluster = dockerHost.getDockerContainerCluster();
        if (cluster.removeMember(machine.getOwner())) {
            LOG.info("Docker Host {}: member {} released", new Object[] { dockerHost.getDockerHostName(), machine });
        } else {
            LOG.info("Docker Host {}: member {} not found for release", new Object[] { dockerHost.getDockerHostName(), machine });
        }
    }

    @Override
    public Map<String, Object> getProvisioningFlags(Collection<String> tags) {
        return MutableMap.of();
    }

    @Override
    public InetAddress getAddress() {
        return machine.getAddress();
    }

    @Override
    public OsDetails getOsDetails() {
        return machine.getOsDetails();
    }

    @Override
    public MachineDetails getMachineDetails() {
        return null;
    }

    @Override
    public DockerHost getOwner() {
        return dockerHost;
    }

    public SshMachineLocation getMachine() {
        return machine;
    }

    public int getCurrentSize() {
        return dockerHost.getCurrentSize();
    }

    public int getMaxSize() {
        return dockerHost.getConfig(DockerHost.DOCKER_CONTAINER_CLUSTER_MAX_SIZE);
    }

    @Override
    public MachineProvisioningLocation<DockerContainerLocation> newSubLocation(Map<?, ?> newFlags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ToStringHelper string() {
        return super.string()
                .add("machine", machine)
                .add("dockerHost", dockerHost);
    }

    @Override
    public List<Entity> getDockerContainerList() {
        return dockerHost.getDockerContainerList();
    }

    @Override
    public List<Entity> getDockerHostList() {
        return Lists.<Entity>newArrayList(dockerHost);
    }

    @Override
    public DockerInfrastructure getDockerInfrastructure() {
        return ((DockerHostLocation) getParent()).getDockerInfrastructure();
    }


}
