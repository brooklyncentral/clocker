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
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.render.RendererHints;
import brooklyn.config.render.RendererHints.Hint;
import brooklyn.config.render.RendererHints.NamedActionWithUrl;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityAndAttribute;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.container.DockerAttributes;
import brooklyn.entity.container.DockerCallbacks;
import brooklyn.entity.container.DockerUtils;
import brooklyn.entity.container.docker.DockerContainer;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.entity.container.weave.WeaveContainer;
import brooklyn.entity.container.weave.WeaveInfrastructure;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.dynamic.DynamicLocation;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.networking.subnet.PortForwarder;
import brooklyn.networking.subnet.SubnetTier;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.mutex.MutexSupport;
import brooklyn.util.mutex.WithMutexes;
import brooklyn.util.net.Cidr;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.text.Strings;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class DockerHostLocation extends AbstractLocation implements MachineProvisioningLocation<DockerContainerLocation>, DockerVirtualLocation,
        DynamicLocation<DockerHost, DockerHostLocation>, WithMutexes, Closeable {

    /** serialVersionUID */
    private static final long serialVersionUID = -1453203257759956820L;

    private static final Logger LOG = LoggerFactory.getLogger(DockerHostLocation.class);

    public static final String CONTAINER_MUTEX = "container";

    @SetFromFlag("mutex")
    private transient WithMutexes mutexSupport;

    @SetFromFlag("machine")
    private SshMachineLocation machine;

    @SetFromFlag("jcloudsLocation")
    private JcloudsLocation jcloudsLocation;

    @SetFromFlag("portForwarder")
    private PortForwarder portForwarder;

    @SetFromFlag("owner")
    private DockerHost dockerHost;

    @SetFromFlag("repository")
    private String repository;

    @SetFromFlag("images")
    private ConcurrentMap<String, CountDownLatch> images = Maps.newConcurrentMap();

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

        if (mutexSupport == null) {
            mutexSupport = new MutexSupport();
        }

        if (repository == null) {
            repository = machine.getId();
        }
    }

    public DockerContainerLocation obtain() throws NoMachinesAvailableException {
        return obtain(Maps.<String,Object>newLinkedHashMap());
    }

    @Override
    public DockerContainerLocation obtain(Map<?,?> flags) throws NoMachinesAvailableException {
        acquireMutex(CONTAINER_MUTEX, "Obtaining container");
        try {
            // Lookup entity from context or flags
            Object context = flags.get(LocationConfigKeys.CALLER_CONTEXT.getName());
            if (context != null && !(context instanceof Entity)) {
                throw new IllegalStateException("Invalid location context: " + context);
            }
            Entity entity = (Entity) context;

            // Configure the entity
            LOG.info("Configuring entity {} via subnet {}", entity, dockerHost.getSubnetTier());
            ((AbstractEntity) entity).setConfigEvenIfOwned(SubnetTier.PORT_FORWARDING_MANAGER, dockerHost.getSubnetTier().getPortForwardManager());
            ((AbstractEntity) entity).setConfigEvenIfOwned(SubnetTier.PORT_FORWARDER, portForwarder);
            if (getOwner().getConfig(WeaveInfrastructure.ENABLED)) {
                WeaveContainer weave = getOwner().getAttribute(WeaveContainer.WEAVE_CONTAINER);
                ((AbstractEntity) entity).setConfigEvenIfOwned(SubnetTier.SUBNET_CIDR, weave.getConfig(WeaveContainer.WEAVE_CIDR));
            } else {
                ((AbstractEntity) entity).setConfigEvenIfOwned(SubnetTier.SUBNET_CIDR, Cidr.UNIVERSAL);
            }
            configureEnrichers((AbstractEntity) entity);

            // Add the entity Dockerfile if configured
            String dockerfile = entity.getConfig(DockerAttributes.DOCKERFILE_URL);
            String imageId = entity.getConfig(DockerAttributes.DOCKER_IMAGE_ID);
            String imageName = DockerUtils.imageName(entity, dockerfile, repository);

            // Lookup image ID or build new image from Dockerfile
            LOG.warn("ImageName for entity {}: {}", entity, imageName);
            String imageList = dockerHost.runDockerCommand("images --no-trunc " + Os.mergePaths(repository, imageName));
            if (Strings.containsLiteral(imageList, imageName)) {
                // Wait until committed before continuing
                waitForImage(imageName);

                // Look up imageId again
                imageList = dockerHost.runDockerCommand("images --no-trunc " + Os.mergePaths(repository, imageName));
                imageId = Strings.getFirstWordAfter(imageList, "latest");
                LOG.info("Found image {} for entity: {}", imageName, imageId);

                // Skip install phase
                ((AbstractEntity) entity).setConfigEvenIfOwned(SoftwareProcess.SKIP_INSTALLATION, true);
            } else {
                // Set commit command at post-install
                insertCallback(entity, SoftwareProcess.POST_INSTALL_COMMAND, DockerCallbacks.commit());

                if (Strings.isNonBlank(dockerfile)) {
                    if (imageId != null) {
                        LOG.warn("Ignoring container imageId {} as dockerfile URL is set: {}", imageId, dockerfile);
                    }
                    imageId = dockerHost.createSshableImage(dockerfile, imageName);
                }
                if (Strings.isBlank(imageId)) {
                    imageId = getOwner().getAttribute(DockerHost.DOCKER_IMAGE_ID);
                }

                // Tag image name and create latch
                images.putIfAbsent(imageName, new CountDownLatch(1));
                dockerHost.runDockerCommand(String.format("tag %s %s:latest", imageId, Os.mergePaths(repository, imageName)));
            }

            // Set subnet address pre install
            insertCallback(entity, SoftwareProcess.PRE_INSTALL_COMMAND, DockerCallbacks.subnetAddress());

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
            Entity added = cluster.addNode(machine, containerFlags);
            if (added == null) {
                throw new NoMachinesAvailableException(String.format("Failed to create container at %s", dockerHost.getDockerHostName()));
            } else {
                Entities.start(added, ImmutableList.of(machine));
            }
            DockerContainer dockerContainer = (DockerContainer) added;

            // Save the container attributes
            ((EntityLocal) dockerContainer).setAttribute(DockerContainer.IMAGE_ID, imageId);
            ((EntityLocal) dockerContainer).setAttribute(DockerContainer.IMAGE_NAME, imageName);
            ((EntityLocal) dockerContainer).setAttribute(DockerContainer.HARDWARE_ID, hardwareId);

            // Link the container to the entity
            ((EntityLocal) entity).setAttribute(DockerContainer.CONTAINER, dockerContainer);
            ((EntityLocal) entity).setAttribute(DockerContainer.CONTAINER_ID, dockerContainer.getContainerId());

            return dockerContainer.getDynamicLocation();
        } finally {
            releaseMutex(CONTAINER_MUTEX);
        }
    }

    private void insertCallback(Entity entity, ConfigKey<String> commandKey, String callback) {
        String command = entity.getConfig(commandKey);
        if (Strings.isNonBlank(command)) {
            command = BashCommands.chain(command, callback);
        } else {
            command = DockerCallbacks.subnetAddress();
        }
        ((AbstractEntity) entity).setConfigEvenIfOwned(commandKey, command);
    }

    public void waitForImage(String imageName) {
        try {
            CountDownLatch latch = images.get(imageName);
            if (latch != null) latch.await(15, TimeUnit.MINUTES);
        } catch (InterruptedException ie) {
            throw Exceptions.propagate(ie);
        }
    }

    public void markImage(String imageName) {
        CountDownLatch latch = images.get(imageName);
        if (latch != null) latch.countDown();
    }

    private void configureEnrichers(AbstractEntity entity) {
        for (AttributeSensor sensor : Iterables.filter(entity.getEntityType().getSensors(), AttributeSensor.class)) {
            if (DockerUtils.URL_SENSOR_NAMES.contains(sensor.getName()) ||
                    sensor.getName().endsWith(".url") ||
                    URI.class.isAssignableFrom(sensor.getType())) {
                AttributeSensor<String> target = DockerUtils.<String>mappedSensor(sensor);
                entity.addEnricher(dockerHost.getSubnetTier().uriTransformingEnricher(
                        EntityAndAttribute.supplier(entity, sensor), target));
                Set<Hint<?>> hints = RendererHints.getHintsFor(sensor);
                for (Hint<?> hint : hints) {
                    RendererHints.register(target, (NamedActionWithUrl) hint);
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Mapped URL sensor: origin={}, mapped={}", sensor.getName(), target.getName());
                }
            } else if (PortAttributeSensorAndConfigKey.class.isAssignableFrom(sensor.getClass())) {
                AttributeSensor<String> target = DockerUtils.mappedPortSensor((PortAttributeSensorAndConfigKey) sensor);
                entity.addEnricher(dockerHost.getSubnetTier().hostAndPortTransformingEnricher(
                        EntityAndAttribute.supplier(entity, sensor), target));
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Mapped port sensor: origin={}, mapped={}", sensor.getName(), target.getName());
                }
            }
        }
    }

    @Override
    public void release(DockerContainerLocation machine) {
        acquireMutex(CONTAINER_MUTEX, "Releasing container " + machine);
        try {
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
        } finally {
            releaseMutex(CONTAINER_MUTEX);
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

    public String getRepository() {
        return repository;
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

    @Override
    public MachineProvisioningLocation<DockerContainerLocation> newSubLocation(Map<?, ?> newFlags) {
        throw new UnsupportedOperationException();
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
        LOG.info("Close called on Docker host {}: {}", machine, this);
        try {
            machine.close();
        } catch (Exception e) {
            LOG.info("{}: Closing Docker host: {}", e.getMessage(), this);
            throw Exceptions.propagate(e);
        } finally {
            LOG.info("Docker host closed: {}", this);
        }
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
                .add("machine", machine)
                .add("jcloudsLocation", jcloudsLocation)
                .add("dockerHost", dockerHost);
    }

}
