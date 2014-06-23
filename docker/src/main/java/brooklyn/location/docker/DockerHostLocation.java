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

import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import io.cloudsoft.networking.subnet.PortForwarder;
import io.cloudsoft.networking.subnet.SubnetTier;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.render.RendererHints;
import brooklyn.config.render.RendererHints.Hint;
import brooklyn.config.render.RendererHints.NamedActionWithUrl;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
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
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.dynamic.DynamicLocation;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.net.Cidr;
import brooklyn.util.os.Os;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.Strings;

import com.google.common.base.Charsets;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;

public class DockerHostLocation extends AbstractLocation implements MachineProvisioningLocation<DockerContainerLocation>, DockerVirtualLocation,
        DynamicLocation<DockerHost, DockerHostLocation>, Closeable {

    /** serialVersionUID */
	private static final long serialVersionUID = -1453203257759956820L;

	private static final Logger LOG = LoggerFactory.getLogger(DockerHostLocation.class);

    @SetFromFlag("mutex")
    private Object mutex;

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

    @Override
    public void configure(Map properties) {
        if (mutex == null) {
            mutex = new Object[0];
        }
        super.configure(properties);
    }

    public DockerContainerLocation obtain() throws NoMachinesAvailableException {
        return obtain(Maps.<String,Object>newLinkedHashMap());
    }

    @Override
    public DockerContainerLocation obtain(Map<?,?> flags) throws NoMachinesAvailableException {
    	synchronized (mutex) {
	        Integer maxSize = dockerHost.getConfig(DockerHost.DOCKER_CONTAINER_CLUSTER_MAX_SIZE);
	        Integer currentSize = dockerHost.getAttribute(DockerAttributes.DOCKER_CONTAINER_COUNT);
	        if (LOG.isDebugEnabled()) {
	            LOG.debug("Docker host {}: {} containers, max {}", new Object[] { dockerHost.getDockerHostName(), currentSize, maxSize });
	        }
	        if (currentSize != null && currentSize >= maxSize) {
	            throw new NoMachinesAvailableException(String.format("Limit of %d containers reached at %s", maxSize, dockerHost.getDockerHostName()));
	        }

	        // Lookup entity from context or flags
	        Object context = flags.get(LocationConfigKeys.CALLER_CONTEXT.getName());
	        if (context == null) context = flags.get("entity");
	        if (context != null && !(context instanceof Entity)) {
	            throw new IllegalStateException("Invalid location context: " + context);
	        }
	        Entity entity = (Entity) context;

	        // Configure the entity
	        LOG.info("Configuring entity {} via subnet {}", entity, dockerHost.getSubnetTier());
	        ((AbstractEntity) entity).setConfigEvenIfOwned(SubnetTier.PORT_FORWARDING_MANAGER, dockerHost.getSubnetTier().getPortForwardManager());
	        ((AbstractEntity) entity).setConfigEvenIfOwned(SubnetTier.PORT_FORWARDER, portForwarder);
	        ((AbstractEntity) entity).setConfigEvenIfOwned(SubnetTier.SUBNET_CIDR, Cidr.UNIVERSAL);
	        configureEnrichers((AbstractEntity) entity);

	        // TODO choose Dockerfile based on entity interfaces such as UsesJava

	        // Add the entity Dockerfile if configured
	        String dockerfile = entity.getConfig(DockerAttributes.DOCKERFILE_URL);
	        String imageId = entity.getConfig(DockerAttributes.DOCKER_IMAGE_ID);
	        String imageName = null;
	        if (Strings.isNonBlank(dockerfile)) {
	            if (imageId != null) {
	                LOG.warn("Ignoring container imageId {} as dockerfile URL is set: {}", imageId, dockerfile);
	            }
	            // Lookup image ID or build new image from Dockerfile
	            imageName = Identifiers.makeIdFromHash(Hashing.md5().hashString(dockerfile, Charsets.UTF_8).asLong()).toLowerCase();
	            String imageList = dockerHost.runDockerCommand("images --no-trunc " + Os.mergePaths("brooklyn", imageName));
	            if (Strings.containsLiteral(imageList, imageName)) {
	                imageId = Strings.getFirstWordAfter(imageList, "latest");
	            } else {
	                imageId = dockerHost.createSshableImage(dockerfile, imageName);
	            }
	        } else if (Strings.isBlank(imageId)) {
	            imageId = getOwner().getAttribute(DockerHost.DOCKER_IMAGE_ID);
	        }

	        // Look up hardware ID
	        String hardwareId = entity.getConfig(DockerAttributes.DOCKER_HARDWARE_ID);
	        if (Strings.isEmpty(hardwareId)) {
	            hardwareId = getOwner().getConfig(DockerAttributes.DOCKER_HARDWARE_ID);
	        }

	        // Create new Docker container in the host cluster
	        LOG.info("Starting container with imageId {} and hardwareId {} at {}", new Object[] { imageId, hardwareId, machine });
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

	        // Save the container attributes on the entity
	        DockerContainer dockerContainer = (DockerContainer) added.get();
	        ((EntityLocal) dockerContainer).setAttribute(DockerContainer.IMAGE_ID, imageId);
	        ((EntityLocal) dockerContainer).setAttribute(DockerContainer.IMAGE_NAME, imageName);
	        ((EntityLocal) dockerContainer).setAttribute(DockerContainer.HARDWARE_ID, hardwareId);
	        return dockerContainer.getDynamicLocation();
    	}
    }

    private void configureEnrichers(AbstractEntity entity) {
        for (Sensor<?> sensor : entity.getEntityType().getSensors()) {
            if (DockerAttributes.URL_SENSOR_NAMES.contains(sensor.getName())) {
                AttributeSensor<String> original = Sensors.newStringSensor(sensor.getName(), sensor.getDescription());
                AttributeSensor<String> target = Sensors.newSensorWithPrefix("mapped.", original);
                entity.addEnricher(dockerHost.getSubnetTier().uriTransformingEnricher(original, target));

                Set<Hint<?>> hints = RendererHints.getHintsFor(sensor, NamedActionWithUrl.class);
                for (Hint<?> hint : hints) {
                    RendererHints.register(target, (NamedActionWithUrl) hint);
                }
            } else if (PortAttributeSensorAndConfigKey.class.isAssignableFrom(sensor.getClass())) {
                AttributeSensor<Integer> original = Sensors.newIntegerSensor(sensor.getName());
                AttributeSensor<String> target = Sensors.newStringSensor("mapped." + sensor.getName(), sensor.getDescription() + " (Docker mapping)");
                entity.addEnricher(dockerHost.getSubnetTier().hostAndPortTransformingEnricher(original, target));
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Mapped port sensor: origin={}, mapped={}", original.getName(), target.getName());
                }
            }
        }
    }

    @Override
    public void release(DockerContainerLocation machine) {
    	synchronized (mutex) {
	        LOG.info("Releasing {}", machine);
	        DynamicCluster cluster = dockerHost.getDockerContainerCluster();
	        DockerContainer container = machine.getOwner();
	        if (cluster.removeMember(container)) {
	            LOG.info("Docker Host {}: member {} released", dockerHost.getDockerHostName(), machine);
	        } else {
	            LOG.warn("Docker Host {}: member {} not found for release", dockerHost.getDockerHostName(), machine);
	        }

	        // Now close and unmange the container
	        try {
	            machine.close();
	            container.stop();
	        } catch (Exception e) {
	            LOG.warn("Error stopping container: " + container, e);
	            Exceptions.propagateIfFatal(e);
	        } finally {
	            Entities.unmanage(container);
	        }
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
        return ((DockerLocation) getParent()).getDockerInfrastructure();
    }

    @Override
    public void close() throws IOException {
        machine.close();
        LOG.info("Close called on Docker host location: {}", this);
    }

}
