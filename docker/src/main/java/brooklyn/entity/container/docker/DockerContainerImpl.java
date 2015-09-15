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
package brooklyn.entity.container.docker;

import static java.lang.String.format;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.CaseFormat;
import com.google.common.base.CharMatcher;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;

import org.jclouds.compute.domain.Processor;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.docker.compute.options.DockerTemplateOptions;
import org.jclouds.net.domain.IpPermission;
import org.jclouds.net.domain.IpProtocol;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.location.NoMachinesAvailableException;
import org.apache.brooklyn.api.location.OsDetails;
import org.apache.brooklyn.api.location.PortRange;
import org.apache.brooklyn.api.mgmt.LocationManager;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.feed.ConfigToAttributes;
import org.apache.brooklyn.core.location.LocationConfigKeys;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.core.location.cloud.CloudLocationConfig;
import org.apache.brooklyn.core.location.dynamic.DynamicLocation;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.stock.BasicStartableImpl;
import org.apache.brooklyn.entity.stock.DelegateEntity;
import org.apache.brooklyn.feed.function.FunctionFeed;
import org.apache.brooklyn.feed.function.FunctionPollConfig;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsLocationConfig;
import org.apache.brooklyn.location.jclouds.JcloudsSshMachineLocation;
import org.apache.brooklyn.location.jclouds.templates.PortableTemplateBuilder;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.internal.ssh.SshTool;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.net.Cidr;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;

import brooklyn.entity.container.DockerAttributes;
import brooklyn.entity.container.DockerUtils;
import brooklyn.location.docker.DockerContainerLocation;
import brooklyn.location.docker.DockerHostLocation;
import brooklyn.networking.portforwarding.subnet.JcloudsPortforwardingSubnetLocation;
import brooklyn.networking.sdn.SdnAgent;
import brooklyn.networking.sdn.SdnAttributes;
import brooklyn.networking.subnet.SubnetTier;

/**
 * A single Docker container.
 */
public class DockerContainerImpl extends BasicStartableImpl implements DockerContainer {

    private static final Logger LOG = LoggerFactory.getLogger(DockerContainer.class);

    private transient FunctionFeed status;

    @Override
    public void init() {
        LOG.info("Starting Docker container id {}", getId());
        super.init();

        AtomicInteger counter = config().get(DOCKER_INFRASTRUCTURE).sensors().get(DockerInfrastructure.DOCKER_CONTAINER_COUNTER);
        String dockerContainerName = config().get(DOCKER_CONTAINER_NAME);
        String dockerContainerNameFormat = config().get(DOCKER_CONTAINER_NAME_FORMAT);
        if (Strings.isBlank(dockerContainerName) && Strings.isNonBlank(dockerContainerNameFormat)) {
            dockerContainerName = format(dockerContainerNameFormat, getId(), counter.incrementAndGet());
        }
        if (Strings.isNonBlank(dockerContainerName)) {
            dockerContainerName = CharMatcher.BREAKING_WHITESPACE.trimAndCollapseFrom(dockerContainerName, '-');
            setDisplayName(CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, dockerContainerName));
            sensors().set(DOCKER_CONTAINER_NAME, dockerContainerName);
        }

        ConfigToAttributes.apply(this, DOCKER_INFRASTRUCTURE);
        ConfigToAttributes.apply(this, DOCKER_HOST);
        ConfigToAttributes.apply(this, ENTITY);
    }

    protected void connectSensors() {
        status = FunctionFeed.builder()
                .entity(this)
                .period(Duration.seconds(15))
                .poll(new FunctionPollConfig<String, String>(DOCKER_CONTAINER_NAME)
                        .period(Duration.minutes(1))
                        .callable(new Callable<String>() {
                                @Override
                                public String call() throws Exception {
                                    String containerId = getContainerId();
                                    if (containerId == null) return "";
                                    String name = getDockerHost().runDockerCommand("inspect -f {{.Name}} " + containerId);
                                    return Strings.removeFromStart(name, "/");
                                }
                        })
                        .onFailureOrException(Functions.constant("")))
                .poll(new FunctionPollConfig<Boolean, Boolean>(SERVICE_UP)
                        .callable(new Callable<Boolean>() {
                                @Override
                                public Boolean call() throws Exception {
                                    String containerId = getContainerId();
                                    if (containerId == null) return false;
                                    return Strings.isNonBlank(getDockerHost().runDockerCommand("inspect -f {{.Id}} " + containerId));
                                }
                        })
                        .onFailureOrException(Functions.constant(Boolean.FALSE)))
                .poll(new FunctionPollConfig<Boolean, Boolean>(CONTAINER_RUNNING)
                        .callable(new Callable<Boolean>() {
                                @Override
                                public Boolean call() throws Exception {
                                    String containerId = getContainerId();
                                    if (containerId == null) return false;
                                    String running = getDockerHost().runDockerCommand("inspect -f {{.State.Running}} " + containerId);
                                    return Strings.isNonBlank(running) && Boolean.parseBoolean(Strings.trim(running));
                                }
                        })
                        .onFailureOrException(Functions.constant(Boolean.FALSE)))
                .poll(new FunctionPollConfig<Boolean, Boolean>(CONTAINER_PAUSED)
                        .callable(new Callable<Boolean>() {
                                @Override
                                public Boolean call() throws Exception {
                                    String containerId = getContainerId();
                                    if (containerId == null) return false;
                                    String running = getDockerHost().runDockerCommand("inspect -f {{.State.Paused}} " + containerId);
                                    return Strings.isNonBlank(running) && Boolean.parseBoolean(Strings.trim(running));
                                }
                        })
                        .onFailureOrException(Functions.constant(Boolean.FALSE)))
                .build();
    }

    public void disconnectSensors() {
        if (status != null) status.stop();
    }

    @Override
    public Entity getRunningEntity() {
        return sensors().get(ENTITY);
    }

    public void setRunningEntity(Entity entity) {
        sensors().set(ENTITY, entity);
    }

    @Override
    public String getDockerContainerName() {
        return sensors().get(DOCKER_CONTAINER_NAME);
    }

    @Override
    public String getContainerId() {
        return sensors().get(CONTAINER_ID);
    }

    @Override
    public SshMachineLocation getMachine() {
        return sensors().get(SSH_MACHINE_LOCATION);
    }

    @Override
    public DockerHost getDockerHost() {
        return (DockerHost) config().get(DOCKER_HOST);
    }

    @Override
    public String getShortName() {
        return "Docker Container";
    }

    @Override
    public DockerContainerLocation getDynamicLocation() {
        return (DockerContainerLocation) sensors().get(DYNAMIC_LOCATION);
    }

    @Override
    public boolean isLocationAvailable() {
        return getDynamicLocation() != null;
    }

    @Override
    public void shutDown() {
        String dockerContainerName = sensors().get(DockerContainer.DOCKER_CONTAINER_NAME);
        LOG.info("Stopping {}", dockerContainerName);
        getDockerHost().runDockerCommand("kill " + getContainerId());
    }

    @Override
    public void pause() {
        String dockerContainerName = sensors().get(DockerContainer.DOCKER_CONTAINER_NAME);
        LOG.info("Pausing {}", dockerContainerName);
        getDockerHost().runDockerCommand("stop " + getContainerId());
    }

    @Override
    public void resume() {
        String dockerContainerName = sensors().get(DockerContainer.DOCKER_CONTAINER_NAME);
        LOG.info("Resuming {}", dockerContainerName);
        getDockerHost().runDockerCommand("start " + getContainerId());
    }

    /**
     * Remove the container from the host.
     * <p>
     * Should only be called when the container is not running.
     */
    private void removeContainer() {
        final String dockerContainerName = sensors().get(DockerContainer.DOCKER_CONTAINER_NAME);
        LOG.info("Removing {}", dockerContainerName);
        getDockerHost().runDockerCommand("rm " + getContainerId());
    }

    private DockerTemplateOptions getDockerTemplateOptions() {
        Entity entity = getRunningEntity();
        Map<String,Object> entityFlags = MutableMap.copyOf(entity.config().get(SoftwareProcess.PROVISIONING_PROPERTIES));
        DockerTemplateOptions options = new DockerTemplateOptions();

        // Use DockerHost hostname for the container
        Boolean useHostDns = entity.config().get(DOCKER_USE_HOST_DNS_NAME);
        if (useHostDns == null) useHostDns = config().get(DOCKER_USE_HOST_DNS_NAME);
        if (useHostDns != null && useHostDns) {
            // FIXME does not seem to work on Softlayer, should set HOSTNAME or SUBNET_HOSTNAME
            String hostname = getDockerHost().sensors().get(Attributes.HOSTNAME);
            String address = getDockerHost().sensors().get(Attributes.ADDRESS);
            if (hostname.equalsIgnoreCase(address)) {
                options.hostname(getDockerContainerName());
            } else {
                options.hostname(hostname);
            }
        }

        // CPU shares
        Integer cpuShares = entity.config().get(DOCKER_CPU_SHARES);
        if (cpuShares == null) cpuShares = config().get(DOCKER_CPU_SHARES);
        if (cpuShares != null) {
            // TODO set based on number of cores available in host divided by cores requested in flags
            Integer hostCores = getDockerHost().getDynamicLocation().getMachine().getMachineDetails().getHardwareDetails().getCpuCount();
            Integer minCores = entity.config().get(JcloudsLocationConfig.MIN_CORES);
            if (minCores == null) {
                minCores = (Integer) entityFlags.get(JcloudsLocationConfig.MIN_CORES.getName());
            }
            if (minCores == null) {
                TemplateBuilder template = (TemplateBuilder) entityFlags.get(JcloudsLocationConfig.TEMPLATE_BUILDER.getName());
                if (template != null) {
                    minCores = 0;
                    for (Processor cpu : template.build().getHardware().getProcessors()) {
                        minCores = minCores + (int) cpu.getCores();
                    }
                }
            }
            if (minCores != null) {
                double ratio = (double) minCores / (double) (hostCores != null ? hostCores : 1);
                LOG.info("Cores: host {}, min {}, ratio {}", new Object[] { hostCores, minCores, ratio });
            }
        }
        if (cpuShares != null) options.cpuShares(cpuShares);

        // Memory
        Integer memory = entity.config().get(DOCKER_MEMORY);
        if (memory == null) memory = config().get(DOCKER_MEMORY);
        if (memory != null) {
            // TODO set based on memory available in host divided by memory requested in flags
            Integer hostRam = getDockerHost().getDynamicLocation().getMachine().getMachineDetails().getHardwareDetails().getRam();
            Integer minRam = (Integer) entity.config().get(JcloudsLocationConfig.MIN_RAM);
            if (minRam == null) {
                minRam = (Integer) entityFlags.get(JcloudsLocationConfig.MIN_RAM.getName());
            }
            if (minRam == null) {
                TemplateBuilder template = (TemplateBuilder) entityFlags.get(JcloudsLocationConfig.TEMPLATE_BUILDER.getName());
                if (template != null) {
                    minRam = template.build().getHardware().getRam();
                }
            }
            if (minRam != null) {
                double ratio = (double) minRam / (double) hostRam;
                LOG.info("Memory: host {}, min {}, ratio {}", new Object[] { hostRam, minRam, ratio });
            }
        }
        if (memory != null) options.memory(memory);

        // Volumes
        Map<String, String> volumes = MutableMap.copyOf(getDockerHost().sensors().get(DockerHost.DOCKER_HOST_VOLUME_MAPPING));
        Map<String, String> mapping = entity.config().get(DockerHost.DOCKER_HOST_VOLUME_MAPPING);
        if (mapping != null) {
            for (String source : mapping.keySet()) {
                if (Urls.isUrlWithProtocol(source)) {
                    String path = getDockerHost().deployArchive(source);
                    volumes.put(path, mapping.get(source));
                } else {
                    volumes.put(source, mapping.get(source));
                }
            }
        }
        List<String> exports = entity.config().get(DockerContainer.DOCKER_CONTAINER_VOLUME_EXPORT);
        if (exports != null) {
            for (String dir : exports) {
                volumes.put(dir, dir);
            }
        }
        sensors().set(DockerAttributes.DOCKER_VOLUME_MAPPING, volumes);
        entity.sensors().set(DockerAttributes.DOCKER_VOLUME_MAPPING, volumes);
        options.volumes(volumes);

        // Environment
        List<String> environment = MutableList.of();
        Map<String,Object> dockerEnvironment = config().get(DockerContainer.DOCKER_CONTAINER_ENVIRONMENT);
        if (dockerEnvironment != null) {
            environment.add(Joiner.on(":").withKeyValueSeparator("=").join(dockerEnvironment));
        }
        Map<String,Object> entityEnvironment = entity.config().get(DockerContainer.DOCKER_CONTAINER_ENVIRONMENT);
        if (entityEnvironment != null) {
            environment.add(Joiner.on(":").withKeyValueSeparator("=").join(entityEnvironment));
        }
        Map<String, Object> containerEnvironment = MutableMap.of();
        for (String entry : environment) {
            List<String> split = Splitter.on('=').trimResults().splitToList(entry);
            String key = split.get(0);
            String value = split.get(1);
            containerEnvironment.put(key,  value);
        }
        sensors().set(DockerContainer.DOCKER_CONTAINER_ENVIRONMENT, containerEnvironment);
        entity.sensors().set(DockerContainer.DOCKER_CONTAINER_ENVIRONMENT, containerEnvironment);
        options.env(environment);

        // Direct port mappings
        Map<Integer, Integer> bindings = MutableMap.copyOf(entity.config().get(DockerAttributes.DOCKER_PORT_BINDINGS));
        if (bindings == null || bindings.isEmpty()) {
            bindings = MutableMap.of();
            List<PortAttributeSensorAndConfigKey> entityPortConfig = entity.config().get(DockerAttributes.DOCKER_DIRECT_PORT_CONFIG);
            if (entityPortConfig != null) {
                for (PortAttributeSensorAndConfigKey key : entityPortConfig) {
                    PortRange range = entity.config().get(key);
                    if (range != null && !range.isEmpty()) {
                        Integer port = range.iterator().next();
                        if (port != null) {
                            bindings.put(port,  port);
                        }
                    }
                }
            }
            List<Integer> entityPorts = entity.config().get(DockerAttributes.DOCKER_DIRECT_PORTS);
            if (entityPorts != null) {
                for (Integer port : entityPorts) {
                    bindings.put(port, port);
                }
            }
        }
        sensors().set(DockerAttributes.DOCKER_PORT_BINDINGS, bindings);
        entity.sensors().set(DockerAttributes.DOCKER_PORT_BINDINGS, bindings);
        if (bindings.size() > 0) {
            options.portBindings(bindings);
        }

        // Inbound ports
        Collection<Integer> entityOpenPorts = getRequiredOpenPorts(entity);
        options.inboundPorts(Ints.toArray(entityOpenPorts));

        // Log for debugging without password
        LOG.debug("Docker options for {}: {}", getDockerHost(), options);

        // Set login password from the Docker host
        options.overrideLoginPassword(getDockerHost().getPassword());

        return options;
    }

    @Nullable
    private String getSshHostAddress() {
        DockerHost dockerHost = getDockerHost();
        OsDetails osDetails = dockerHost.getDynamicLocation().getMachine().getMachineDetails().getOsDetails();
        if (osDetails.isMac()) {
            String address = dockerHost.execCommand("boot2docker ip");
            LOG.debug("The boot2docker IP address is {}", Strings.trim(address));
            return Strings.trim(address);
        } else {
            return null;
        }
    }

    public void configurePortBindings(DockerHost host, Entity entity) {
        Map<Integer, Integer> bindings = entity.sensors().get(DockerAttributes.DOCKER_PORT_BINDINGS);
        if (bindings.size() > 0) {
            Collection<IpPermission> permissions = MutableList.of();
            for (Integer hostPort : bindings.keySet()) {
                IpPermission portAccess = IpPermission.builder()
                        .ipProtocol(IpProtocol.TCP)
                        .fromPort(hostPort)
                        .toPort(hostPort)
                        .cidrBlock(Cidr.UNIVERSAL.toString())
                        .build();
                permissions.add(portAccess);
            }
            LOG.debug("Adding security group entries for ports on {}: {}", entity, Iterables.toString(bindings.keySet()));
            host.addIpPermissions(permissions);
        }
    }

    /**
     * Create a new {@link DockerContainerLocation} wrapping a machine from the host's {@link JcloudsLocation}.
     */
    @Override
    public DockerContainerLocation createLocation(Map flags) {
        DockerHost dockerHost = getDockerHost();
        DockerHostLocation host = dockerHost.getDynamicLocation();
        SubnetTier subnetTier = dockerHost.getSubnetTier();
        Entity entity = getRunningEntity();

        // Configure the container options based on the host and the running entity
        DockerTemplateOptions options = getDockerTemplateOptions();

        // Check the running entity for alternative container name
        String containerName = entity.config().get(DOCKER_CONTAINER_NAME);
        if (Strings.isBlank(containerName)) {
            containerName = sensors().get(DOCKER_CONTAINER_NAME);
        }
        if (Strings.isNonBlank(containerName)) {
            options.nodeNames(ImmutableList.of(DockerUtils.allowed(containerName)));
        }

        // put these fields on the location so it has the info it needs to create the subnet
        Map<?, ?> dockerFlags = MutableMap.<Object, Object>builder()
                .put(JcloudsLocationConfig.TEMPLATE_BUILDER, new PortableTemplateBuilder().options(options))
                .put(JcloudsLocationConfig.IMAGE_ID, config().get(DOCKER_IMAGE_ID))
                .put(JcloudsLocationConfig.HARDWARE_ID, config().get(DOCKER_HARDWARE_ID))
                .put(LocationConfigKeys.USER, "root")
                .put(LocationConfigKeys.PASSWORD, config().get(DOCKER_PASSWORD))
                .put(SshTool.PROP_PASSWORD, config().get(DOCKER_PASSWORD))
                .put(LocationConfigKeys.PRIVATE_KEY_DATA, null)
                .put(LocationConfigKeys.PRIVATE_KEY_FILE, null)
                .put(CloudLocationConfig.WAIT_FOR_SSHABLE, false)
                .put(JcloudsLocationConfig.INBOUND_PORTS, getRequiredOpenPorts(entity))
                .put(JcloudsLocation.USE_PORT_FORWARDING, true)
                .put(JcloudsLocation.PORT_FORWARDER, subnetTier.getPortForwarderExtension())
                .put(JcloudsLocation.PORT_FORWARDING_MANAGER, subnetTier.getPortForwardManager())
                .put(JcloudsPortforwardingSubnetLocation.PORT_FORWARDER, subnetTier.getPortForwarder())
                .put(SubnetTier.SUBNET_CIDR, Cidr.CLASS_B)
                .build();

        try {
            // Create a new container using jclouds Docker driver
            JcloudsSshMachineLocation container = (JcloudsSshMachineLocation) host.getJcloudsLocation().obtain(dockerFlags);
            String containerId = container.getNode().getId();
            sensors().set(CONTAINER_ID, containerId);

            // Configure the host to allow remote access to bound container ports
            configurePortBindings(dockerHost, entity);

            // Link the entity to the container
            entity.sensors().set(DockerContainer.DOCKER_INFRASTRUCTURE, dockerHost.getInfrastructure());
            entity.sensors().set(DockerContainer.DOCKER_HOST, dockerHost);
            entity.sensors().set(DockerContainer.CONTAINER, this);
            entity.sensors().set(DockerContainer.CONTAINER_ID, containerId);

            // If SDN is enabled, attach networks
            if (config().get(SdnAttributes.SDN_ENABLE)) {
                SdnAgent agent = Entities.attributeSupplierWhenReady(dockerHost, SdnAgent.SDN_AGENT).get();

                // Save attached network list
                List<String> networks = Lists.newArrayList(entity.getApplicationId());
                Collection<String> extra = entity.config().get(SdnAttributes.NETWORK_LIST);
                if (extra != null) networks.addAll(extra);
                sensors().set(SdnAttributes.ATTACHED_NETWORKS, networks);
                entity.sensors().set(SdnAttributes.ATTACHED_NETWORKS, networks);

                // Save container addresses
                Set<String> addresses = Sets.newHashSet();
                for (String networkId : networks) {
                    InetAddress address = agent.attachNetwork(containerId, networkId);
                    addresses.add(address.getHostAddress().toString());
                    if (networkId.equals(entity.getApplicationId())) {
                        sensors().set(Attributes.SUBNET_ADDRESS, address.getHostAddress());
                        sensors().set(Attributes.SUBNET_HOSTNAME, address.getHostAddress());
                        entity.sensors().set(Attributes.SUBNET_ADDRESS, address.getHostAddress());
                        entity.sensors().set(Attributes.SUBNET_HOSTNAME, address.getHostAddress());
                    }
                }
                sensors().set(CONTAINER_ADDRESSES, addresses);
                entity.sensors().set(CONTAINER_ADDRESSES, addresses);
            }

            // Create our wrapper location around the container
            LocationSpec<DockerContainerLocation> spec = LocationSpec.create(DockerContainerLocation.class)
                    .parent(host)
                    .configure(flags)
                    .configure(DynamicLocation.OWNER, this)
                    .configure("machine", container) // the underlying JcloudsLocation
                    .configure(container.config().getBag().getAllConfig())
                    .configureIfNotNull(SshMachineLocation.SSH_HOST, getSshHostAddress())
                    .displayName(getDockerContainerName());
            DockerContainerLocation location = getManagementContext().getLocationManager().createLocation(spec);

            sensors().set(DYNAMIC_LOCATION, location);
            sensors().set(LOCATION_NAME, location.getId());

            LOG.info("New Docker container location {} created", location);
            return location;
        } catch (NoMachinesAvailableException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public void deleteLocation() {
        DockerContainerLocation location = getDynamicLocation();

        if (location != null) {
            try {
                location.close();
            } catch (IOException ioe) {
                LOG.debug("Error closing container location", ioe);
            }
            LocationManager mgr = getManagementContext().getLocationManager();
            if (mgr.isManaged(location)) {
                mgr.unmanage(location);
            }
        }

        sensors().set(DYNAMIC_LOCATION, null);
        sensors().set(LOCATION_NAME, null);
    }

    /** @return the ports required for a specific entity */
    protected Collection<Integer> getRequiredOpenPorts(Entity entity) {
        Set<Integer> ports = MutableSet.of(22);
        for (ConfigKey<?> k: entity.getEntityType().getConfigKeys()) {
            if (PortRange.class.isAssignableFrom(k.getType())) {
                PortRange p = (PortRange) entity.config().get(k);
                if (p != null && !p.isEmpty()) ports.add(p.iterator().next());
            }
        }
        List<Integer> entityOpenPorts = MutableList.copyOf(entity.config().get(DockerAttributes.DOCKER_OPEN_PORTS));
        entityOpenPorts.addAll(entity.sensors().get(DockerAttributes.DOCKER_PORT_BINDINGS).values());
        if (entityOpenPorts.size() > 0) {
            // Create config and sensor for these ports
            for (int i = 0; i < entityOpenPorts.size(); i++) {
                Integer port = entityOpenPorts.get(i);
                String name = String.format("docker.port.%02d", port);
                entity.sensors().set(Sensors.newIntegerSensor(name), port);
                entity.config().set(ConfigKeys.newConfigKey(PortRange.class, name), PortRanges.fromInteger(port));
            }

            ports.addAll(entityOpenPorts);
        }
        for (Entity child : entity.getChildren()) {
            ports.addAll(getRequiredOpenPorts(child));
        }
        LOG.debug("getRequiredOpenPorts detected default {} for {}", ports, entity);
        return ports;
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        ServiceStateLogic.setExpectedState(this, Lifecycle.STARTING);

        Boolean started = config().get(SoftwareProcess.ENTITY_STARTED);
        if (Boolean.TRUE.equals(started)) {
            DockerHost dockerHost = getDockerHost();
            DockerHostLocation host = dockerHost.getDynamicLocation();
            sensors().set(DockerContainer.IMAGE_ID, config().get(DOCKER_IMAGE_ID));
            sensors().set(DockerContainer.IMAGE_NAME, config().get(DockerAttributes.DOCKER_IMAGE_NAME));
            sensors().set(SSH_MACHINE_LOCATION, host.getMachine());
        } else {
            Map<String, ?> flags = MutableMap.copyOf(config().get(LOCATION_FLAGS));
            DockerContainerLocation location = createLocation(flags);
            sensors().set(SSH_MACHINE_LOCATION, location.getMachine());
        }

        connectSensors();

        super.start(locations);

        ServiceStateLogic.setExpectedState(this, Lifecycle.RUNNING);
    }

    @Override
    public void rebind() {
        super.rebind();

        if (status == null) {
            connectSensors();
        }
    }

    @Override
    public void stop() {
        Lifecycle state = sensors().get(SERVICE_STATE_ACTUAL);
        if (Lifecycle.STOPPING.equals(state) || Lifecycle.STOPPED.equals(state)) {
            LOG.debug("Ignoring request to stop {} when it is already {}", this, state);
            LOG.trace("Duplicate stop came from: \n" + Joiner.on("\n").join(Thread.getAllStackTraces().get(Thread.currentThread())));
            return;
        }
        LOG.info("Stopping {} when its state is {}", this, sensors().get(SERVICE_STATE_ACTUAL));
        ServiceStateLogic.setExpectedState(this, Lifecycle.STOPPING);

        disconnectSensors();

        // Stop and remove the Docker container running on the host
        shutDown();
        removeContainer();

        sensors().set(SSH_MACHINE_LOCATION, null);
        Boolean started = config().get(SoftwareProcess.ENTITY_STARTED);
        if (!Boolean.TRUE.equals(started)) {
            deleteLocation();
        }

        ServiceStateLogic.setExpectedState(this, Lifecycle.STOPPED);
    }

    @Override
    public String getHostname() {
        return getDockerContainerName(); // XXX or SUBNET_ADDRESS attribute?
    }

    @Override
    public Set<String> getPublicAddresses() {
        return Sets.newHashSet(sensors().get(SoftwareProcess.SUBNET_ADDRESS));
    }

    @Override
    public Set<String> getPrivateAddresses() {
        return sensors().get(CONTAINER_ADDRESSES);
    }

    static {
        RendererHints.register(DOCKER_HOST, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(ENTITY, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(CONTAINER, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
    }

}
