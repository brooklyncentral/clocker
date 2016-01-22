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
package brooklyn.entity.container.docker;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.CharMatcher;
import com.google.common.base.Functions;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import org.jclouds.compute.config.ComputeServiceProperties;
import org.jclouds.compute.domain.OsFamily;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.net.domain.IpPermission;
import org.jclouds.net.domain.IpProtocol;
import org.jclouds.softlayer.SoftLayerApi;
import org.jclouds.softlayer.compute.options.SoftLayerTemplateOptions;
import org.jclouds.softlayer.domain.VirtualGuest;
import org.jclouds.softlayer.features.VirtualGuestApi;
import org.jclouds.softlayer.reference.SoftLayerConstants;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationDefinition;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.api.location.PortRange;
import org.apache.brooklyn.api.mgmt.LocationManager;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.core.effector.ssh.SshEffectorTasks;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityFunctions;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.feed.ConfigToAttributes;
import org.apache.brooklyn.core.location.BasicLocationDefinition;
import org.apache.brooklyn.core.location.Machines;
import org.apache.brooklyn.core.location.geo.LocalhostExternalIpLoader;
import org.apache.brooklyn.core.sensor.DependentConfiguration;
import org.apache.brooklyn.enricher.stock.Enrichers;
import org.apache.brooklyn.entity.group.Cluster;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.entity.group.DynamicGroup;
import org.apache.brooklyn.entity.machine.MachineEntityImpl;
import org.apache.brooklyn.entity.software.base.AbstractSoftwareProcessSshDriver;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.stock.DelegateEntity;
import org.apache.brooklyn.feed.function.FunctionFeed;
import org.apache.brooklyn.feed.function.FunctionPollConfig;
import org.apache.brooklyn.feed.ssh.SshFeed;
import org.apache.brooklyn.feed.ssh.SshPollConfig;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsLocationConfig;
import org.apache.brooklyn.location.jclouds.JcloudsMachineLocation;
import org.apache.brooklyn.location.jclouds.JcloudsSshMachineLocation;
import org.apache.brooklyn.location.jclouds.networking.JcloudsLocationSecurityGroupCustomizer;
import org.apache.brooklyn.location.jclouds.templates.PortableTemplateBuilder;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.policy.ha.ServiceFailureDetector;
import org.apache.brooklyn.policy.ha.ServiceReplacer;
import org.apache.brooklyn.policy.ha.ServiceRestarter;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.QuorumCheck.QuorumChecks;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.core.task.system.ProcessTaskStub.ScriptReturnType;
import org.apache.brooklyn.util.core.task.system.ProcessTaskWrapper;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.net.Cidr;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.ssh.BashCommands;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.text.StringPredicates;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;

import brooklyn.entity.container.DockerAttributes;
import brooklyn.entity.container.DockerUtils;
import brooklyn.entity.container.docker.registry.DockerRegistry;
import brooklyn.entity.nosql.etcd.EtcdNode;
import brooklyn.location.docker.DockerHostLocation;
import brooklyn.location.docker.DockerLocation;
import brooklyn.location.docker.DockerResolver;
import brooklyn.networking.portforwarding.DockerPortForwarder;
import brooklyn.networking.sdn.DockerSdnProvider;
import brooklyn.networking.sdn.SdnAgent;
import brooklyn.networking.sdn.SdnAttributes;
import brooklyn.networking.sdn.SdnProvider;
import brooklyn.networking.sdn.SdnUtils;
import brooklyn.networking.sdn.calico.CalicoNode;
import brooklyn.networking.sdn.weave.WeaveContainer;
import brooklyn.networking.sdn.weave.WeaveNetwork;
import brooklyn.networking.subnet.SubnetTier;
import brooklyn.networking.subnet.SubnetTierImpl;

/**
 * The host running the Docker service.
 */
public class DockerHostImpl extends MachineEntityImpl implements DockerHost {

    private static final Logger LOG = LoggerFactory.getLogger(DockerHost.class);

    private transient SshFeed serviceUpIsRunningFeed;
    private transient FunctionFeed scan;

    @Override
    public void init() {
        LOG.info("Starting Docker host id {}", getId());
        super.init();

        AtomicInteger counter = config().get(DOCKER_INFRASTRUCTURE).sensors().get(DockerInfrastructure.DOCKER_HOST_COUNTER);
        String dockerHostName = String.format(config().get(DockerHost.DOCKER_HOST_NAME_FORMAT), getId(), counter.incrementAndGet());
        setDisplayName(dockerHostName);
        sensors().set(DOCKER_HOST_NAME, dockerHostName);

        // Set a password for this host's containers
        String password = config().get(DOCKER_PASSWORD);
        if (Strings.isBlank(password)) {
            password = Identifiers.makeRandomId(8);
            config().set(DOCKER_PASSWORD, password);
        }

        ConfigToAttributes.apply(this, DOCKER_INFRASTRUCTURE);

        EntitySpec<DockerContainer> dockerContainerSpec = EntitySpec.create(config().get(DOCKER_CONTAINER_SPEC));
        dockerContainerSpec.configure(DockerContainer.DOCKER_HOST, this)
                .configure(DockerContainer.DOCKER_INFRASTRUCTURE, getInfrastructure());
        if (config().get(DockerInfrastructure.HA_POLICY_ENABLE)) {
            dockerContainerSpec.policy(PolicySpec.create(ServiceRestarter.class)
                    .configure(ServiceRestarter.FAILURE_SENSOR_TO_MONITOR, ServiceFailureDetector.ENTITY_FAILED));
        }
        sensors().set(DOCKER_CONTAINER_SPEC, dockerContainerSpec);

        DynamicCluster containers = addChild(EntitySpec.create(DynamicCluster.class)
                .configure(Cluster.INITIAL_SIZE, 0)
                .configure(DynamicCluster.QUARANTINE_FAILED_ENTITIES, false)
                .configure(DynamicCluster.MEMBER_SPEC, dockerContainerSpec)
                .configure(DynamicCluster.RUNNING_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty())
                .configure(DynamicCluster.UP_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty())
                .displayName("Docker Containers"));
        if (config().get(DockerInfrastructure.HA_POLICY_ENABLE)) {
            containers.policies().add(PolicySpec.create(ServiceReplacer.class)
                    .configure(ServiceReplacer.FAILURE_SENSOR_TO_MONITOR, ServiceRestarter.ENTITY_RESTART_FAILED));
        }
        sensors().set(DOCKER_CONTAINER_CLUSTER, containers);

        enrichers().add(Enrichers.builder()
                .propagating(ImmutableMap.of(DynamicCluster.GROUP_SIZE, DockerAttributes.DOCKER_CONTAINER_COUNT))
                .from(containers)
                .build());
    }

    @Override
    public String getIconUrl() { return "classpath://docker-logo.png"; }

    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
        Collection<Integer> ports = super.getRequiredOpenPorts();
        if (config().get(DockerInfrastructure.SDN_ENABLE)) {
            Entity sdn = sensors().get(DockerHost.DOCKER_INFRASTRUCTURE)
                    .sensors().get(DockerInfrastructure.SDN_PROVIDER);
            if (SdnUtils.isSdnProvider(getInfrastructure(), "WeaveNetwork")) {
                Integer weavePort = sdn.config().get(WeaveNetwork.WEAVE_PORT);
                if (weavePort != null) ports.add(weavePort);
                Integer proxyPort = sdn.config().get(WeaveContainer.WEAVE_PROXY_PORT);
                if (proxyPort != null) ports.add(proxyPort);
            }
            if (SdnUtils.isSdnProvider(getInfrastructure(), "CalicoNetwork")) {
                PortRange etcdPort = sdn.config().get(EtcdNode.ETCD_CLIENT_PORT);
                if (etcdPort != null) ports.add(etcdPort.iterator().next());
                Integer powerstripPort = sdn.config().get(CalicoNode.POWERSTRIP_PORT);
                if (powerstripPort != null) ports.add(powerstripPort);
            }
        }
        return ports;
    }

    @Override
    protected Map<String, Object> obtainProvisioningFlags(MachineProvisioningLocation location) {
        Map<String, Object> flags = MutableMap.copyOf(super.obtainProvisioningFlags(location));
        flags.putAll(config().get(PROVISIONING_FLAGS));

        // Configure template for host virtual machine
        if (location instanceof JcloudsLocation) {
            Set<ConfigKey<?>> imageChoiceToRespect = ImmutableSet.<ConfigKey<?>>of(
                    JcloudsLocationConfig.TEMPLATE_BUILDER,
                    JcloudsLocationConfig.IMAGE_CHOOSER,
                    JcloudsLocationConfig.IMAGE_ID,
                    JcloudsLocationConfig.IMAGE_NAME_REGEX,
                    JcloudsLocationConfig.IMAGE_DESCRIPTION_REGEX,
                    JcloudsLocationConfig.OS_FAMILY,
                    JcloudsLocationConfig.OS_VERSION_REGEX,
                    JcloudsLocationConfig.OS_64_BIT);
            Set<ConfigKey<?>> hardwareChoiceToRespect = ImmutableSet.<ConfigKey<?>>of(
                    JcloudsLocationConfig.HARDWARE_ID,
                    JcloudsLocationConfig.MIN_RAM,
                    JcloudsLocationConfig.MIN_CORES,
                    JcloudsLocationConfig.MIN_DISK);
            Map<String, Object> existingConfigOptions = ((JcloudsLocation) location).config().getBag().getAllConfig();
            TemplateBuilder template = (TemplateBuilder) flags.get(JcloudsLocationConfig.TEMPLATE_BUILDER.getName());

            boolean overrideImageChoice = true;
            for (ConfigKey<?> key : imageChoiceToRespect) {
                if (existingConfigOptions.get(key.getName()) != null || flags.get(key.getName()) != null) {
                    overrideImageChoice = false;
                    break;
                }
            }

            boolean overrideHardwareChoice = true;
            for (ConfigKey<?> key : hardwareChoiceToRespect) {
                if (existingConfigOptions.get(key.getName()) != null || flags.get(key.getName()) != null) {
                    overrideHardwareChoice = false;
                    break;
                }
            }

            if (overrideImageChoice) {
                LOG.debug("Customising image choice for {}", this);
                template = new PortableTemplateBuilder();
                if (isJcloudsLocation(location, "google-compute-engine")) {
                    template.osFamily(OsFamily.CENTOS).osVersionMatches("6");
                } else if (isJcloudsLocation(location, SoftLayerConstants.SOFTLAYER_PROVIDER_NAME)) {
                    template.osFamily(OsFamily.UBUNTU).osVersionMatches("14.04");
                } else {
                    template.osFamily(OsFamily.UBUNTU).osVersionMatches("15.04");
                }
                template.os64Bit(true);
                flags.put(JcloudsLocationConfig.TEMPLATE_BUILDER.getName(), template);
            } else {
                LOG.debug("Not modifying existing image configuration for {}", this);
            }

            if (overrideHardwareChoice) {
                LOG.debug("Customising hardware choice for {}", this);
                if (template != null) {
                    template.minRam(2048);
                    flags.put(JcloudsLocationConfig.TEMPLATE_BUILDER.getName(), template);
                } else {
                    flags.put(JcloudsLocationConfig.MIN_RAM.getName(), 2048);
                }
            } else {
                LOG.debug("Not modifying existing hardware configuration for {}", this);
            }

            // Configure security groups for host virtual machine
            String securityGroup = config().get(DockerInfrastructure.SECURITY_GROUP);
            if (Strings.isNonBlank(securityGroup)) {
                if (isJcloudsLocation(location, "google-compute-engine")) {
                    flags.put("networkName", securityGroup);
                } else {
                    flags.put("securityGroups", securityGroup);
                }
            } else {
                //if (!isJcloudsLocation(location, SoftLayerConstants.SOFTLAYER_PROVIDER_NAME)) {
                    flags.put(JcloudsLocationConfig.JCLOUDS_LOCATION_CUSTOMIZERS.getName(),
                            ImmutableList.of(JcloudsLocationSecurityGroupCustomizer.getInstance(getApplicationId())));
                //}
            }

            // Setup SoftLayer template options
            if (isJcloudsLocation(location, SoftLayerConstants.SOFTLAYER_PROVIDER_NAME)) {
                if (template == null) template = new PortableTemplateBuilder();
                SoftLayerTemplateOptions options = new SoftLayerTemplateOptions();
                options.portSpeed(Objects.firstNonNull(options.getPortSpeed(), 1000));

                // Try and determine if we need to set a VLAN for this host (overriding location)
                Integer vlanOption = options.getPrimaryBackendNetworkComponentNetworkVlanId();
                Entity sdnProviderAttribute = sensors().get(DOCKER_INFRASTRUCTURE)
                         .sensors().get(DockerInfrastructure.SDN_PROVIDER);
                Optional<Integer> vlanConfig = Optional.absent();
                if (sdnProviderAttribute != null) {
                    vlanConfig = Optional.fromNullable(sdnProviderAttribute.config().get(SdnProvider.VLAN_ID));
                }

                Integer vlanId = vlanOption == null ? vlanConfig.orNull() : vlanOption;
                if (vlanId == null) {
                    // If a previous host has been configured, look up the VLAN id
                    int count = sensors().get(DOCKER_INFRASTRUCTURE).sensors().get(DockerInfrastructure.DOCKER_HOST_COUNT);
                    if (count > 1 && !sensors().get(DynamicCluster.FIRST_MEMBER) && sdnProviderAttribute != null) {
                        Task<Integer> lookup = DependentConfiguration.attributeWhenReady(sdnProviderAttribute, SdnProvider.VLAN_ID);
                        vlanId = DynamicTasks.submit(lookup, this).getUnchecked();
                    }
                }
                if (vlanId != null) {
                    options.primaryBackendNetworkComponentNetworkVlanId(vlanId);
                }
                template.options(options);
                flags.put(JcloudsLocationConfig.TEMPLATE_BUILDER.getName(), template);
            }
        }
        return flags;
    }

    private boolean isJcloudsLocation(MachineProvisioningLocation location, String providerName) {
        return location instanceof JcloudsLocation
                && ((JcloudsLocation) location).getProvider().equals(providerName);
    }

    @Override
    public Integer resize(Integer desiredSize) {
        return getDockerContainerCluster().resize(desiredSize);
    }

    @Override
    public String getShortName() {
        return "Docker Host";
    }

    @Override
    public Integer getCurrentSize() {
        return getDockerContainerCluster().getCurrentSize();
    }

    @Override
    public Class<?> getDriverInterface() {
        return DockerHostDriver.class;
    }

    @Override
    public DockerHostDriver getDriver() {
        return (DockerHostDriver) super.getDriver();
    }

    @Override
    public Integer getDockerPort() {
        return sensors().get(DOCKER_SSL_PORT);
    }

    @Override
    public String getDockerHostName() {
        return sensors().get(DOCKER_HOST_NAME);
    }

    @Override
    public List<Entity> getDockerContainerList() {
        return ImmutableList.copyOf(getDockerContainerCluster().getMembers());
    }

    @Override
    public DockerInfrastructure getInfrastructure() {
        return (DockerInfrastructure) config().get(DOCKER_INFRASTRUCTURE);
    }

    @Override
    public String getPassword() {
        return config().get(DOCKER_PASSWORD);
    }

    /** {@inheritDoc} */
    @Override
    public String buildImage(String dockerFile, @Nullable String entrypoint, @Nullable String contextArchive, String name, boolean useSsh, Map<String, Object> substitutions) {
        String imageId = getDriver().buildImage(dockerFile, Optional.fromNullable(entrypoint), Optional.fromNullable(contextArchive), name, useSsh, substitutions);
        LOG.debug("Successfully created image {} ({})", new Object[] { imageId, name });
        return imageId;
    }

    @Override
    public String layerSshableImageOnFullyQualified(String fullyQualifiedName) {
        String imageId = getDriver().layerSshableImageOn(fullyQualifiedName);
        LOG.debug("Successfully added SSHable layer as {}", fullyQualifiedName);
        return imageId;
    }

    @Override
    public String layerSshableImageOn(String baseImage, String tag) {
        String imageId = getDriver().layerSshableImageOn(baseImage+ ":" +tag);
        LOG.debug("Successfully added SSHable layer as {} from {}", imageId, baseImage);
        return imageId;
    }


    /** {@inheritDoc} */
    @Override
    public String runDockerCommand(String command) {
        return runDockerCommandTimeout(command, Duration.FIVE_MINUTES);
    }

    /** {@inheritDoc} */
    @Override
    public String runDockerCommandTimeout(String command, Duration timeout) {
        // FIXME Set DOCKER_OPTS values in command-line for when running on localhost
        String stdout = execCommandTimeout(BashCommands.sudo(String.format("docker %s", command)), timeout);
        LOG.debug("Successfully executed Docker {}: {}", Strings.getFirstWord(command), Strings.getFirstLine(stdout));
        return stdout;
    }

    /** {@inheritDoc} */
    @Override
    public String deployArchive(String url) {
        Tasks.setBlockingDetails("Deploy " + url);
        try {
            return getDriver().deployArchive(url);
        } finally {
            Tasks.resetBlockingDetails();
        }
    }

    @Override
    public DockerHostLocation getDynamicLocation() {
        return (DockerHostLocation) sensors().get(DYNAMIC_LOCATION);
    }

    @Override
    public boolean isLocationAvailable() {
        return getDynamicLocation() != null;
    }

    @Override
    public DynamicCluster getDockerContainerCluster() { return sensors().get(DOCKER_CONTAINER_CLUSTER); }

    @Override
    public JcloudsLocation getJcloudsLocation() { return sensors().get(JCLOUDS_DOCKER_LOCATION); }

    @Override
    public SubnetTier getSubnetTier() { return sensors().get(DOCKER_HOST_SUBNET_TIER); }

    @Override
    public int execCommandStatus(String command) {
        return execCommandStatusTimeout(command, Duration.seconds(15));
    }

    @Override
    public int execCommandStatusTimeout(String command, Duration timeout) {
        ProcessTaskWrapper<Object> task = SshEffectorTasks.ssh(command)
                .environmentVariables(((AbstractSoftwareProcessSshDriver) getDriver()).getShellEnvironment())
                .returning(ScriptReturnType.EXIT_CODE)
                .allowingNonZeroExitCode()
                .machine(getMachine())
                .summary(command)
                .newTask();

        try {
            Object result = DynamicTasks.queueIfPossible(task)
                    .executionContext(this)
                    .orSubmitAsync()
                    .asTask()
                    .get(timeout);
            return (Integer) result;
        } catch (TimeoutException te) {
            throw new IllegalStateException("Timed out running command: " + command);
        } catch (Exception e) {
            Integer exitCode = task.getExitCode();
            LOG.warn("Command failed, return code {}: {}", exitCode == null ? -1 : exitCode, task.getStderr());
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public Optional<String> getImageNamed(String name) {
        return getImageNamed(name, "latest");
    }

    @Override
    public Optional<String> getImageNamed(String name, String tag) {
        String imageList = runDockerCommand("images --no-trunc " + name);
        return Optional.fromNullable(Strings.getFirstWordAfter(imageList, tag));
    }

    /**
     * Create a new {@link DockerHostLocation} wrapping the machine we are starting in.
     */
    @Override
    public DockerHostLocation createLocation(Map<String, ?> flags) {
        DockerInfrastructure infrastructure = getInfrastructure();
        DockerLocation docker = infrastructure.getDynamicLocation();
        String locationName = docker.getId() + "-" + getDockerHostName();

        String locationSpec = String.format(DockerResolver.DOCKER_HOST_MACHINE_SPEC, infrastructure.getId(), getId()) + String.format(":(name=\"%s\")", locationName);
        sensors().set(LOCATION_SPEC, locationSpec);

        LocationDefinition definition = new BasicLocationDefinition(locationName, locationSpec, flags);
        Location location = getManagementContext().getLocationRegistry().resolve(definition);
        sensors().set(DYNAMIC_LOCATION, location);
        sensors().set(LOCATION_NAME, location.getId());

        LOG.info("New Docker host location {} created", location);
        return (DockerHostLocation) location;
    }

    @Override
    public void deleteLocation() {
        DockerHostLocation location = getDynamicLocation();

        if (location != null) {
            LocationManager mgr = getManagementContext().getLocationManager();
            if (mgr.isManaged(location)) {
                mgr.unmanage(location);
            }
        }

        sensors().set(DYNAMIC_LOCATION, null);
        sensors().set(LOCATION_NAME, null);
    }

    @Override
    public void configureSecurityGroups() {
        Collection<IpPermission> permissions = getIpPermissions();
        addIpPermissions(permissions);
    }


    @Override
    public void removeIpPermissions(Collection<IpPermission> permissions) {
        Location location = getDriver().getLocation();
        String securityGroup = config().get(DockerInfrastructure.SECURITY_GROUP);
        if (Strings.isBlank(securityGroup)) {
            if (!(location instanceof JcloudsSshMachineLocation)) {
                LOG.info("{} not running in a JcloudsSshMachineLocation, not removing ip permissions", this);
                return;
            }
            // TODO check GCE compatibility?
            JcloudsMachineLocation machine = (JcloudsMachineLocation) location;
            JcloudsLocationSecurityGroupCustomizer customizer = JcloudsLocationSecurityGroupCustomizer.getInstance(getApplicationId());

            // Serialize access across the whole infrastructure as the security groups are a shared resource
            synchronized (getInfrastructure().getInfrastructureMutex()) {
                LOG.debug("Removing permissions from security groups {}: {}", machine, permissions);
                customizer.removePermissionsFromLocation(machine, permissions);
            }
        }
    }

    @Override
    public void addIpPermissions(Collection<IpPermission> permissions) {
        Location location = getDriver().getLocation();
        String securityGroup = config().get(DockerInfrastructure.SECURITY_GROUP);
        if (Strings.isBlank(securityGroup)) {
            if (!(location instanceof JcloudsSshMachineLocation)) {
                LOG.info("{} not running in a JcloudsSshMachineLocation, not adding ip permissions", this);
                return;
            }
            // TODO check GCE compatibility?
            JcloudsMachineLocation machine = (JcloudsMachineLocation) location;
            JcloudsLocationSecurityGroupCustomizer customizer = JcloudsLocationSecurityGroupCustomizer.getInstance(getApplicationId());

            // Serialize access across the whole infrastructure as the security groups are a shared resource
            synchronized (getInfrastructure().getInfrastructureMutex()) {
                LOG.debug("Applying custom security groups to {}: {}", machine, permissions);
                customizer.addPermissionsToLocation(machine, permissions);
            }
        }
    }

    /**
     * @return Extra IP permissions to be configured on this entity's location.
     */
    protected Collection<IpPermission> getIpPermissions() {
        List<IpPermission> permissions = MutableList.of();

        String publicIpCidr = LocalhostExternalIpLoader.getLocalhostIpWithin(Duration.minutes(1)) + "/32";
        permissions.addAll(getClockerPermisionsForCIDR(publicIpCidr));

        if (config().get(ADD_LOCALHOST_PERMISSION)) {
            String localhostAddress = null;
            try {
                localhostAddress = InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException e) {
                throw Exceptions.propagate(e);
            }

            String localhostCIDR = localhostAddress + "/32";
            if (Strings.isNonEmpty(localhostAddress) && !publicIpCidr.equals(localhostCIDR)) {
                permissions.addAll(getClockerPermisionsForCIDR(localhostCIDR));
            }
        }
        IpPermission dockerPortForwarding = IpPermission.builder()
                .ipProtocol(IpProtocol.TCP)
                .fromPort(32768)
                .toPort(65534)
                .cidrBlock(Cidr.UNIVERSAL.toString())
                .build();
        permissions.add(dockerPortForwarding);

        if (config().get(DockerInfrastructure.DOCKER_SHOULD_START_REGISTRY) && sensors().get(DynamicGroup.FIRST_MEMBER)) {
            IpPermission dockerRegistryPort = IpPermission.builder()
                    .ipProtocol(IpProtocol.TCP)
                    .fromPort(config().get(DockerRegistry.DOCKER_REGISTRY_PORT))
                    .toPort(config().get(DockerRegistry.DOCKER_REGISTRY_PORT))
                    .cidrBlock(Cidr.UNIVERSAL.toString())
                    .build();
            permissions.add(dockerRegistryPort);
        }

        return permissions;
    }

    private List<IpPermission> getClockerPermisionsForCIDR(String cidr) {
        List<IpPermission> permissions = MutableList.of();
        IpPermission dockerPort = IpPermission.builder()
                .ipProtocol(IpProtocol.TCP)
                .fromPort(sensors().get(DockerHost.DOCKER_PORT))
                .toPort(sensors().get(DockerHost.DOCKER_PORT))
                .cidrBlock(cidr)
                .build();
        IpPermission dockerSslPort = IpPermission.builder()
                .ipProtocol(IpProtocol.TCP)
                .fromPort(sensors().get(DockerHost.DOCKER_SSL_PORT))
                .toPort(sensors().get(DockerHost.DOCKER_SSL_PORT))
                .cidrBlock(cidr)
                .build();
        permissions.add(dockerPort);
        permissions.add(dockerSslPort);

        if (config().get(SdnAttributes.SDN_ENABLE)) {
            DockerSdnProvider provider = (DockerSdnProvider) (sensors().get(DockerHost.DOCKER_INFRASTRUCTURE).sensors().get(DockerInfrastructure.SDN_PROVIDER));
            Collection<IpPermission> sdnPermissions = provider.getIpPermissions(cidr);
            permissions.addAll(sdnPermissions);
        }

        return permissions;
    }

    @Override
    protected void preStart() {
        configureSecurityGroups();

        // Save the VLAN id for this machine
        MachineProvisioningLocation location = getInfrastructure().getDynamicLocation().getProvisioner();
        if (isJcloudsLocation(location, SoftLayerConstants.SOFTLAYER_PROVIDER_NAME)) {
            Entity sdnProviderAttribute = sensors().get(DOCKER_INFRASTRUCTURE).sensors().get(DockerInfrastructure.SDN_PROVIDER);
            if (sdnProviderAttribute != null) {
                Integer vlanId = sdnProviderAttribute.sensors().get(SdnProvider.VLAN_ID);
                if (vlanId == null) {
                    VirtualGuestApi api = ((JcloudsLocation) location).getComputeService().getContext().unwrapApi(SoftLayerApi.class).getVirtualGuestApi();
                    JcloudsSshMachineLocation machine = (JcloudsSshMachineLocation) getDriver().getLocation();
                    Long serverId = Long.parseLong(machine.getJcloudsId());
                    VirtualGuest guest = api.getVirtualGuestFiltered(serverId, "primaryBackendNetworkComponent;primaryBackendNetworkComponent.networkVlan");
                    vlanId = guest.getPrimaryBackendNetworkComponent().getNetworkVlan().getId();
                    Integer vlanNumber = guest.getPrimaryBackendNetworkComponent().getNetworkVlan().getVlanNumber();
                    ((EntityInternal) sensors().get(DOCKER_INFRASTRUCTURE).sensors().get(DockerInfrastructure.SDN_PROVIDER)).sensors().set(SdnProvider.VLAN_ID, vlanId);
                    LOG.debug("Recorded VLAN #{} with id {} for server id {}: {}", new Object[] { vlanNumber, vlanId, serverId, this });
                } else {
                    LOG.debug("Found VLAN {}: {}", new Object[] { vlanId, this });
                }
            }
        }

        Integer dockerPort = getDockerPort();
        boolean tlsEnabled = true;
        if (SdnUtils.isSdnProvider(getInfrastructure(), "CalicoNetwork")) {
            dockerPort = sensors().get(DockerHost.DOCKER_INFRASTRUCTURE)
                    .sensors().get(DockerInfrastructure.SDN_PROVIDER)
                    .config().get(CalicoNode.POWERSTRIP_PORT);
            tlsEnabled = false;
        }
        if (SdnUtils.isSdnProvider(getInfrastructure(), "WeaveNetwork")) {
            dockerPort = sensors().get(DockerHost.DOCKER_INFRASTRUCTURE)
                    .sensors().get(DockerInfrastructure.SDN_PROVIDER)
                    .config().get(WeaveContainer.WEAVE_PROXY_PORT);
            tlsEnabled = true;
        }
        Maybe<SshMachineLocation> found = Machines.findUniqueMachineLocation(getLocations(), SshMachineLocation.class);
        String dockerLocationSpec = String.format("jclouds:docker:%s://%s:%s",
                tlsEnabled ? "https" : "http", found.get().getSshHostAndPort().getHostText(), dockerPort);

        String certPath, keyPath;
        if (config().get(DockerInfrastructure.DOCKER_GENERATE_TLS_CERTIFICATES)) {
            getMachine().copyTo(ResourceUtils.create().getResourceFromUrl(config().get(DockerInfrastructure.DOCKER_CA_CERTIFICATE_PATH)), "ca-cert.pem");
            getMachine().copyTo(ResourceUtils.create().getResourceFromUrl(config().get(DockerInfrastructure.DOCKER_CA_KEY_PATH)), "ca-key.pem");
            getMachine().copyTo(ResourceUtils.create().getResourceFromUrl("classpath://brooklyn/entity/container/docker/create-certs.sh"), "create-certs.sh");
            getMachine().execCommands("createCertificates",
                    ImmutableList.of("chmod 755 create-certs.sh", "./create-certs.sh " + sensors().get(ADDRESS)));
            certPath = Os.mergePaths(Os.tmp(), getId() + "-cert.pem");
            getMachine().copyFrom("client-cert.pem", certPath);
            keyPath = Os.mergePaths(Os.tmp(), getId() + "-key.pem");
            getMachine().copyFrom("client-key.pem", keyPath);
        } else {
            certPath = config().get(DockerInfrastructure.DOCKER_CLIENT_CERTIFICATE_PATH);
            keyPath = config().get(DockerInfrastructure.DOCKER_CLIENT_KEY_PATH);
        }
        JcloudsLocation jcloudsLocation = (JcloudsLocation) getManagementContext().getLocationRegistry()
                .resolve(dockerLocationSpec, MutableMap.builder()
                        .put("identity", certPath)
                        .put("credential", keyPath)
                        .put(ComputeServiceProperties.IMAGE_LOGIN_USER, "root:" + getPassword())
                        .build());
        sensors().set(JCLOUDS_DOCKER_LOCATION, jcloudsLocation);

        DockerPortForwarder portForwarder = new DockerPortForwarder();
        portForwarder.setManagementContext(getManagementContext());
        portForwarder.init(URI.create(jcloudsLocation.getEndpoint()));
        SubnetTier subnetTier = addChild(EntitySpec.create(SubnetTier.class, SubnetTierImpl.class)
                .configure(SubnetTier.PORT_FORWARDER, portForwarder)
                .configure(SubnetTier.SUBNET_CIDR, Cidr.UNIVERSAL));
        subnetTier.start(ImmutableList.of(found.get()));
        sensors().set(DOCKER_HOST_SUBNET_TIER, subnetTier);

        Map<String, ?> flags = MutableMap.<String, Object>builder()
                .putAll(config().get(LOCATION_FLAGS))
                .put("machine", found.get())
                .put("jcloudsLocation", jcloudsLocation)
                .put("portForwarder", portForwarder)
                .build();
        createLocation(flags);
    }

    @Override
    public void postStart() {
        Entities.waitForServiceUp(this);

        sensors().get(DOCKER_CONTAINER_CLUSTER).sensors().set(SERVICE_UP, Boolean.TRUE);

        if (Boolean.TRUE.equals(sensors().get(DOCKER_INFRASTRUCTURE).config().get(SdnAttributes.SDN_ENABLE))) {
            LOG.info("Waiting on SDN agent");
            SdnAgent agent = Entities.attributeSupplierWhenReady(this, SdnAgent.SDN_AGENT).get();
            Entities.waitForServiceUp(agent);
            LOG.info("SDN agent running: " + agent.sensors().get(SERVICE_UP));
        }

        String imageId = config().get(DOCKER_IMAGE_ID);

        if (Strings.isBlank(imageId)) {
            String dockerfileUrl = config().get(DockerInfrastructure.DOCKERFILE_URL);
            String imageName = DockerUtils.imageName(this, dockerfileUrl);
            imageId = buildImage(dockerfileUrl, null, null, imageName, config().get(DockerHost.DOCKER_USE_SSH), ImmutableMap.<String, Object>of("fullyQualifiedImageName", imageName));
            sensors().set(DOCKER_IMAGE_NAME, imageName);
        }

        sensors().set(DOCKER_IMAGE_ID, imageId);

        scan = scanner();

        // If a registry URL is configured with credentials then log in
        String registryUrl = config().get(DockerInfrastructure.DOCKER_IMAGE_REGISTRY_URL);
        Boolean internalRegistry = config().get(DockerInfrastructure.DOCKER_SHOULD_START_REGISTRY);
        if (Strings.isNonBlank(registryUrl) && !internalRegistry) {
            String username = config().get(DockerInfrastructure.DOCKER_IMAGE_REGISTRY_USERNAME);
            String password = config().get(DockerInfrastructure.DOCKER_IMAGE_REGISTRY_PASSWORD);

            if (Strings.isNonBlank(username) && Strings.isNonBlank(password)) {
                runDockerCommand(String.format("login  -e \"fake@example.org\" -u %s -p %s %s", username, password, registryUrl));
            }
        }
    }

    private FunctionFeed scanner() {
        Duration interval = config().get(SCAN_INTERVAL);
        return FunctionFeed.builder()
                .entity(this)
                .poll(new FunctionPollConfig<Object, Void>(SCAN)
                        .period(interval)
                        .description("Scan Containers")
                        .callable(new Callable<Void>() {
                                @Override
                                public Void call() throws Exception {
                                    scanContainers();
                                    return null;
                                }
                            })
                        .onFailureOrException(Functions.<Void>constant(null)))
                .build();
    }

    @Override
    public void rebind() {
        super.rebind();

        // Restart the container scanner
        if (scan == null) {
            scan = scanner();
        }
    }

    @Override
    public void preStop() {
        if (scan != null && scan.isActivated()) scan.stop();

        SdnAgent agent = sensors().get(SdnAgent.SDN_AGENT);
        if (agent != null && Entities.isManaged(agent)) {
            // Avoid DockerHost -> SdnAgent -> DockerHost stop recursion by invoking
            // the effector instead of agent.stop().
            boolean agentStopped = Entities.invokeEffector(this, agent, Startable.STOP)
                    .blockUntilEnded(Duration.TEN_SECONDS);
            if (!agentStopped) {
                LOG.debug("{} may not have stopped. Proceeding to stop {} anyway", agent, this);
            }
        }

        super.preStop();
    }

    @Override
    public void postStop() {
        super.postStop(); // Currently does nothing

        deleteLocation();
    }

    public void scanContainers() {
        getDynamicLocation().getLock().lock();
        try {
            String output = runDockerCommand("ps");
            List<String> ps = Splitter.on(CharMatcher.anyOf("\r\n")).omitEmptyStrings().splitToList(output);
            if (ps.size() > 1) {
                for (int i = 1; i < ps.size(); i++) {
                    String line = ps.get(i);
                    String id = Strings.getFirstWord(line);
                    Optional<Entity> container = Iterables.tryFind(getDockerContainerCluster().getMembers(),
                            Predicates.compose(StringPredicates.startsWith(id), EntityFunctions.attribute(DockerContainer.CONTAINER_ID)));
                    if (container.isPresent()) continue;

                    // Build a DockerContainer without a locations, as it may not be SSHable
                    String containerId = Strings.getFirstWord(runDockerCommand("inspect --format {{.Id}} " + id));
                    String imageId = Strings.getFirstWord(runDockerCommand("inspect --format {{.Image}} " + id));
                    String imageName = Strings.getFirstWord(runDockerCommand("inspect --format {{.Config.Image}} " + id));
                    EntitySpec<DockerContainer> containerSpec = EntitySpec.create(config().get(DOCKER_CONTAINER_SPEC));
                    containerSpec.configure(SoftwareProcess.ENTITY_STARTED, Boolean.TRUE)
                            .configure(DockerContainer.DOCKER_HOST, this)
                            .configure(DockerContainer.DOCKER_INFRASTRUCTURE, getInfrastructure())
                            .configure(DockerContainer.DOCKER_IMAGE_ID, imageId)
                            .configure(DockerContainer.DOCKER_IMAGE_NAME, imageName)
                            .configure(DockerContainer.LOCATION_FLAGS, MutableMap.<String, Object>of("container", getMachine()));

                    // Create, manage and start the container
                    DockerContainer added = getDockerContainerCluster().addMemberChild(containerSpec);
                    added.sensors().set(DockerContainer.CONTAINER_ID, containerId);
                    added.start(ImmutableList.of(getDynamicLocation().getMachine()));
                }
            }
            for (Entity member : ImmutableList.copyOf(getDockerContainerCluster().getMembers())) {
                final String id = member.sensors().get(DockerContainer.CONTAINER_ID);
                if (id != null) {
                    Optional<String> found = Iterables.tryFind(ps, new Predicate<String>() {
                        @Override
                        public boolean apply(String input) {
                            String firstWord = Strings.getFirstWord(input);
                            return id.startsWith(firstWord);
                        }
                    });
                    if (found.isPresent()) continue;
                }

                // Stop and then remove the container as it is no longer running unless ON_FIRE
                Lifecycle state = member.sensors().get(SERVICE_STATE_ACTUAL);
                if (Lifecycle.ON_FIRE.equals(state) || Lifecycle.STARTING.equals(state)) {
                    continue;
                } else if (Lifecycle.STOPPING.equals(state) || Lifecycle.STOPPED.equals(state)) {
                    getDockerContainerCluster().removeMember(member);
                    getDockerContainerCluster().removeChild(member);
                    Entities.unmanage(member);
                } else {
                    ServiceStateLogic.setExpectedState(member, Lifecycle.STOPPING);
                }
            }
        } finally {
            getDynamicLocation().getLock().unlock();
        }
    }

    @Override
    protected void connectServiceUpIsRunning() {
        //TODO change to HttpFeed with client certificates at some point in the future
        serviceUpIsRunningFeed = SshFeed.builder()
                .entity(this)
                .period(Duration.FIVE_SECONDS)
                .machine(this.getMachine())
                .poll(new SshPollConfig<Boolean>(SERVICE_UP)
                        .command("docker ps")
                        .onSuccess(Functions.constant(true))
                        .onFailureOrException(Functions.constant(false)))
                .build();
    }

    @Override
    protected void disconnectServiceUpIsRunning() {
        if (serviceUpIsRunningFeed != null) serviceUpIsRunningFeed.stop();
    }


    static {
        RendererHints.register(DOCKER_INFRASTRUCTURE, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(DOCKER_CONTAINER_CLUSTER, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
    }

}
