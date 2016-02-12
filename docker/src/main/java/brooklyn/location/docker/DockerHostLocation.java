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
package brooklyn.location.docker;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.api.location.NoMachinesAvailableException;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.location.AbstractLocation;
import org.apache.brooklyn.core.location.LocationConfigKeys;
import org.apache.brooklyn.core.location.dynamic.DynamicLocation;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.net.Cidr;
import org.apache.brooklyn.util.ssh.BashCommands;
import org.apache.brooklyn.util.text.Strings;

import brooklyn.entity.container.DockerAttributes;
import brooklyn.entity.container.DockerCallbacks;
import brooklyn.entity.container.DockerUtils;
import brooklyn.entity.container.docker.DockerContainer;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.networking.common.subnet.PortForwarder;
import brooklyn.networking.sdn.SdnAgent;
import brooklyn.networking.sdn.SdnAttributes;
import brooklyn.networking.sdn.SdnProvider;
import brooklyn.networking.subnet.SubnetTier;

public class DockerHostLocation extends AbstractLocation implements MachineProvisioningLocation<DockerContainerLocation>, DockerVirtualLocation,
        DynamicLocation<DockerHost, DockerHostLocation>, Closeable {

    private static final long serialVersionUID = -1453203257759956820L;

    private static final Logger LOG = LoggerFactory.getLogger(DockerHostLocation.class);

    public static final String CONTAINER_MUTEX = "container";

    private transient ReadWriteLock lock = new ReentrantReadWriteLock();

    @SetFromFlag("machine")
    private SshMachineLocation machine;

    @SetFromFlag("jcloudsLocation")
    private JcloudsLocation jcloudsLocation;

    @SetFromFlag("portForwarder")
    private PortForwarder portForwarder;

    @SetFromFlag("owner")
    private DockerHost dockerHost;

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



    public DockerContainerLocation obtain() throws NoMachinesAvailableException {
        return obtain(Maps.<String,Object>newLinkedHashMap());
    }

    @Override
    public DockerContainerLocation obtain(Map<?,?> flags) throws NoMachinesAvailableException {
        lock.readLock().lock();
        try {
            // Lookup entity from context or flags
            Object context = flags.get(LocationConfigKeys.CALLER_CONTEXT.getName());
            if (context == null || !(context instanceof Entity)) {
                throw new IllegalStateException("Invalid location context: " + context);
            }
            Entity entity = (Entity) context;

            // Flag to configure adding SSHable layer
            boolean useSsh = entity.config().get(DockerContainer.DOCKER_USE_SSH) &&
                    dockerHost.config().get(DockerContainer.DOCKER_USE_SSH);

            // Configure the entity
            LOG.info("Configuring entity {} via subnet {}", entity, dockerHost.getSubnetTier());
            entity.config().set(SubnetTier.PORT_FORWARDING_MANAGER, dockerHost.getSubnetTier().getPortForwardManager());
            entity.config().set(SubnetTier.PORT_FORWARDER, portForwarder);
            if (getOwner().config().get(SdnAttributes.SDN_ENABLE)) {
                SdnAgent agent = getOwner().sensors().get(SdnAgent.SDN_AGENT);
                if (agent == null) {
                    throw new IllegalStateException("SDN agent entity on " + getOwner() + " is null");
                }
                Map<String, Cidr> networks = agent.sensors().get(SdnAgent.SDN_PROVIDER).sensors().get(SdnProvider.SUBNETS);
                entity.config().set(SubnetTier.SUBNET_CIDR, networks.get(entity.getApplicationId()));
            } else {
                entity.config().set(SubnetTier.SUBNET_CIDR, Cidr.UNIVERSAL);
            }

            // Add the entity Dockerfile if configured
            String dockerfile = entity.config().get(DockerAttributes.DOCKERFILE_URL);
            String entrypoint = entity.config().get(DockerAttributes.DOCKERFILE_ENTRYPOINT_URL);
            String contextArchive = entity.config().get(DockerAttributes.DOCKERFILE_CONTEXT_URL);
            String imageId = entity.config().get(DockerAttributes.DOCKER_IMAGE_ID);

            Optional<String> baseImage = Optional.fromNullable(entity.config().get(DockerAttributes.DOCKER_IMAGE_NAME));
            String imageTag = Optional.fromNullable(entity.config().get(DockerAttributes.DOCKER_IMAGE_TAG)).or("latest");

            // TODO incorporate more info (incl registry?)
            String imageName = DockerUtils.imageName(entity, dockerfile);

            // Lookup image ID or build new image from Dockerfile
            LOG.info("ImageName for entity {}: {}", entity, imageName);

            if (dockerHost.getImageNamed(imageName, imageTag).isPresent()) {
                // Wait until committed before continuing - Brooklyn may be midway through its creation.
                waitForImage(imageName);

                // Look up imageId again
                imageId = dockerHost.getImageNamed(imageName, imageTag).get();
                LOG.info("Found image {} for entity: {}", imageName, imageId);

                // Skip install phase
                entity.config().set(SoftwareProcess.SKIP_INSTALLATION, true);
            } else if (baseImage.isPresent()) {
                // Use the repository configured on the entity if present
                Optional<String> imageRepo = Optional.fromNullable(entity.config().get(DockerAttributes.DOCKER_IMAGE_REGISTRY_URL));
                // Otherwise only use the configured repo here if it we created it or it is writeable
                Optional<String> localRepo = Optional.absent();
                if (config().get(DockerInfrastructure.DOCKER_SHOULD_START_REGISTRY) ||
                        config().get(DockerInfrastructure.DOCKER_IMAGE_REGISTRY_WRITEABLE)) {
                    localRepo = Optional.fromNullable(getDockerInfrastructure().sensors().get(DockerAttributes.DOCKER_IMAGE_REGISTRY_URL));;
                }
                imageName = Joiner.on('/').join(Optional.presentInstances(ImmutableList.of(imageRepo.or(localRepo), baseImage)));
                String fullyQualifiedName = imageName + ":" + imageTag;

                if (useSsh) {
                    // Create an SSHable image from the one configured
                    imageId = dockerHost.layerSshableImageOnFullyQualified(fullyQualifiedName);
                    LOG.info("Created SSHable image from {}: {}", fullyQualifiedName, imageId);
                } else {
                    try {
                        dockerHost.runDockerCommand(String.format("pull %s", fullyQualifiedName));
                    } catch (Exception e) {
                        // XXX pulls fail sometimes but issue fixed in Docker 1.9.1
                        LOG.debug("Caught exception pulling {}: {}", fullyQualifiedName, e.getMessage());
                    }
                    imageId = dockerHost.getImageNamed(imageName, imageTag).orNull();
                }
                entity.config().set(SoftwareProcess.SKIP_INSTALLATION, true);
            } else {
                // Push or commit the image, otherwise Clocker will make a new one for the entity once it is installed.
                if (getDockerInfrastructure().config().get(DockerInfrastructure.DOCKER_IMAGE_REGISTRY_WRITEABLE) &&
                        (getDockerInfrastructure().config().get(DockerInfrastructure.DOCKER_SHOULD_START_REGISTRY) ||
                                Strings.isNonBlank(getDockerInfrastructure().sensors().get(DockerInfrastructure.DOCKER_IMAGE_REGISTRY_URL)))) {
                    insertCallback(entity, SoftwareProcess.POST_INSTALL_COMMAND, DockerCallbacks.push());
                } else {
                    insertCallback(entity, SoftwareProcess.POST_INSTALL_COMMAND, DockerCallbacks.commit());
                }

                if (Strings.isNonBlank(dockerfile)) {
                    if (imageId != null) {
                        LOG.warn("Ignoring container imageId {} as dockerfile URL is set: {}", imageId, dockerfile);
                    }
                    Map<String, Object> substitutions = getExtraTemplateSubstitutions(imageName, entity);
                    imageId = dockerHost.buildImage(dockerfile, entrypoint, contextArchive, imageName, useSsh, substitutions);
                }
                if (Strings.isBlank(imageId)) {
                    imageId = getOwner().sensors().get(DockerHost.DOCKER_IMAGE_ID);
                }

                // Tag the image name and create its latch
                images.putIfAbsent(imageName, new CountDownLatch(1));
                dockerHost.runDockerCommand(String.format("tag -f %s %s:latest", imageId, imageName));
            }

            // Look up hardware ID
            String hardwareId = entity.config().get(DockerAttributes.DOCKER_HARDWARE_ID);
            if (Strings.isEmpty(hardwareId)) {
                hardwareId = getOwner().config().get(DockerAttributes.DOCKER_HARDWARE_ID);
            }

            // Fix missing device link for urandom on some containers
            insertCallback(entity, SoftwareProcess.PRE_INSTALL_COMMAND,
                    "if [ ! -e /dev/random ] ; then ln -s /dev/urandom /dev/random ; fi");

            // Create new Docker container in the host cluster
            LOG.info("Starting container with imageId {} and hardwareId {} at {}", new Object[] { imageId, hardwareId, machine });
            Map<Object, Object> containerFlags = MutableMap.builder()
                    .putAll(flags)
                    .put("useSsh", useSsh)
                    .put("entity", entity)
                    .putIfNotNull("imageId", imageId)
                    .putIfNotNull("imageName", imageId == null ? imageName : null)
                    .putIfNotNull("imageTag", imageId == null ? imageTag : null)
                    .putIfNotNull("hardwareId", hardwareId)
                    .build();
            DynamicCluster cluster = dockerHost.getDockerContainerCluster();
            Entity added = cluster.addNode(machine, containerFlags);
            if (added == null) {
                throw new NoMachinesAvailableException(String.format("Failed to create container at %s", dockerHost.getDockerHostName()));
            } else {
                Entities.invokeEffector(entity, added, Startable.START,  MutableMap.of("locations", ImmutableList.of(machine))).getUnchecked();
            }
            DockerContainer dockerContainer = (DockerContainer) added;

            // Save the container attributes
            dockerContainer.sensors().set(DockerContainer.IMAGE_ID, imageId);
            dockerContainer.sensors().set(DockerContainer.IMAGE_NAME, imageName);
            dockerContainer.sensors().set(DockerContainer.HARDWARE_ID, hardwareId);

            // record SDN application network details
            if (getOwner().config().get(SdnAttributes.SDN_ENABLE)) {
                SdnAgent agent = getOwner().sensors().get(SdnAgent.SDN_AGENT);
                Cidr applicationCidr =  agent.sensors().get(SdnAgent.SDN_PROVIDER).getSubnetCidr(entity.getApplicationId());
                entity.sensors().set(SdnProvider.APPLICATION_CIDR, applicationCidr);
                dockerContainer.sensors().set(SdnProvider.APPLICATION_CIDR, applicationCidr);
            }

            return dockerContainer.getDynamicLocation();
        } finally {
            lock.readLock().unlock();
        }
    }

    private Map<String, Object> getExtraTemplateSubstitutions(String imageName, Entity context) {
        Map<String, Object> templateSubstitutions = MutableMap.<String, Object>of("fullyQualifiedImageName", imageName);
        templateSubstitutions.putAll(getOwner().config().get(DockerInfrastructure.DOCKERFILE_SUBSTITUTIONS));

        // Add any extra substitutions on the entity (if present)
        if (context != null) {
            templateSubstitutions.putAll(context.config().get(DockerInfrastructure.DOCKERFILE_SUBSTITUTIONS));
        }

        return templateSubstitutions;
    }

    private void insertCallback(Entity entity, ConfigKey<String> commandKey, String callback) {
        String command = entity.config().get(commandKey);
        if (Strings.isNonBlank(command)) {
            command = BashCommands.chain(String.format("( %s )", command), callback);
        } else {
            command = callback;
        }
        entity.config().set(commandKey, command);
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

    @Override
    public void release(DockerContainerLocation machine) {
        lock.readLock().lock();
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
                container.stop();
                machine.close();
            } catch (Exception e) {
                LOG.warn("Error stopping container: " + container, e);
                Exceptions.propagateIfFatal(e);
            } finally {
                Entities.unmanage(container);
            }
        } finally {
            lock.readLock().unlock();
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

    public Lock getLock() {
        return lock.writeLock();
    }

    @Override
    public ToStringHelper string() {
        return super.string()
                .add("machine", machine)
                .add("jcloudsLocation", jcloudsLocation)
                .add("dockerHost", dockerHost);
    }

}
