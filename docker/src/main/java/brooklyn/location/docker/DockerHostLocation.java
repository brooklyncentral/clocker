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

import io.cloudsoft.networking.subnet.PortForwarder;
import io.cloudsoft.networking.subnet.SubnetTier;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.container.docker.DockerAttributes;
import brooklyn.entity.container.docker.DockerContainer;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.basic.Sensors;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.dynamic.DynamicLocation;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.net.Cidr;
import brooklyn.util.text.Strings;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class DockerHostLocation extends AbstractLocation implements
        MachineProvisioningLocation<DockerContainerLocation>, DockerVirtualLocation,
        DynamicLocation<DockerHost, DockerHostLocation> {

    private static final Logger LOG = LoggerFactory.getLogger(DockerHostLocation.class);

    @SetFromFlag("machine")
    private SshMachineLocation machine;

    @SetFromFlag("jcloudsLocation")
    private JcloudsLocation jcloudsLocation;

    @SetFromFlag("portForwarder")
    private PortForwarder portForwarder;

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

    public DockerContainerLocation obtain() throws NoMachinesAvailableException {
        return obtain(Maps.<String,Object>newLinkedHashMap());
    }

    @Override
    public DockerContainerLocation obtain(Map<?,?> flags) throws NoMachinesAvailableException {
        Integer maxSize = dockerHost.getConfig(DockerHost.DOCKER_CONTAINER_CLUSTER_MAX_SIZE);
        Integer currentSize = dockerHost.getAttribute(DockerAttributes.DOCKER_CONTAINER_COUNT);
        Entity entity = (Entity) flags.get("entity");
        if (LOG.isDebugEnabled()) {
            LOG.debug("Docker host {}: {} containers, max {}", new Object[] { dockerHost.getDockerHostName(), currentSize, maxSize });
        }

        if (currentSize != null && currentSize >= maxSize) {
            throw new NoMachinesAvailableException(String.format("Limit of %d containers reached at %s", maxSize, dockerHost.getDockerHostName()));
        }

        // Configure the entity
        LOG.info("Configuring entity {} via subnet {}", entity, dockerHost.getSubnetTier());
        ((AbstractEntity) entity).setConfigEvenIfOwned(SubnetTier.PORT_FORWARDING_MANAGER, dockerHost.getSubnetTier().getAttribute(SubnetTier.SUBNET_SERVICE_PORT_FORWARDS));
        ((AbstractEntity) entity).setConfigEvenIfOwned(SubnetTier.PORT_FORWARDER, portForwarder);
        ((AbstractEntity) entity).setConfigEvenIfOwned(SubnetTier.SUBNET_CIDR, Cidr.UNIVERSAL);
        configureEnrichers((AbstractEntity) entity);

        // TODO choose Dockerfile based on entity interfaces such as UsesJava

        // Add the entity Dockerfile if configured
        String dockerfile = entity.getConfig(DockerAttributes.DOCKERFILE_URL);
        String imageId = entity.getConfig(DockerAttributes.DOCKER_IMAGE_ID);
        if (Strings.isNonBlank(dockerfile)) {
            if (imageId != null) {
                LOG.warn("Ignoring container imageId {} as dockerfile URL is set: {}", imageId, dockerfile);
            }
            String className = entity.getClass().getName().toLowerCase(Locale.ENGLISH);
            String entityType = DockerAttributes.DOCKERFILE_INVALID_CHARACTERS.trimAndCollapseFrom(className, '-');
            imageId = dockerHost.createSshableImage(dockerfile, entityType);
        } else if (Strings.isBlank(imageId)) {
            imageId = getOwner().getAttribute(DockerHost.DOCKER_IMAGE_ID);
        }

        // Look up hardware ID
        String hardwareId = entity.getConfig(DockerAttributes.DOCKER_HARDWARE_ID);
        if (Strings.isEmpty(hardwareId)) {
            hardwareId = getOwner().getConfig(DockerAttributes.DOCKER_HARDWARE_ID);
        }

        // increase size of Docker container cluster
        LOG.info("Increase size of Docker container cluster at {}", machine);
        Map<Object, Object> containerFlags = MutableMap.builder()
                .putAll(flags)
                .put("entity", entity)
                .putIfNotNull("imageId", imageId)
                .putIfNotNull("hardwareId", hardwareId)
                .build();
        DynamicCluster cluster = dockerHost.getDockerContainerCluster();
        Optional<Entity> added = cluster.addInSingleLocation(machine, containerFlags);
        if (!added.isPresent()) {
            throw new NoMachinesAvailableException(String.format("Failed to create containers. Limit reached at %s", dockerHost.getDockerHostName()));
        }

        DockerContainer dockerContainer = (DockerContainer) added.get();
        return dockerContainer.getDynamicLocation();
    }

    private void configureEnrichers(AbstractEntity entity) {
        for (Sensor<?> sensor : entity.getEntityType().getSensors()) {
            if (DockerAttributes.URL_SENSOR_NAMES.contains(sensor.getName())) {
                AttributeSensor<String> original = Sensors.newStringSensor(sensor.getName());
                AttributeSensor<String> target = Sensors.newSensorWithPrefix("mapped.", original);
                entity.addEnricher(dockerHost.getSubnetTier().uriTransformingEnricher(original, target));
            } else if (DockerAttributes.PORT_SENSOR_NAMES.contains(sensor.getName())) {
                AttributeSensor<Integer> original = Sensors.newIntegerSensor(sensor.getName());
                AttributeSensor<String> target = Sensors.newStringSensor("mapped." + sensor.getName(), sensor.getDescription() + " (Docker mapping)");
                entity.addEnricher(dockerHost.getSubnetTier().hostAndPortTransformingEnricher(original, target));
            }
        }
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
    public DockerHost getOwner() {
        return dockerHost;
    }

    public SshMachineLocation getMachine() {
        return machine;
    }

    public JcloudsLocation getJcloudsLocation() {
        return jcloudsLocation;
    }

    public PortForwarder getPortForwarder() {
        return portForwarder;
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
                .add("jcloudsLocation", jcloudsLocation)
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
