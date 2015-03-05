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

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import org.jclouds.compute.config.ComputeServiceProperties;
import org.jclouds.compute.domain.OsFamily;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.domain.Volume;
import org.jclouds.softlayer.compute.options.SoftLayerTemplateOptions;
import org.jclouds.softlayer.reference.SoftLayerConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.render.RendererHints;
import brooklyn.enricher.Enrichers;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.DelegateEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityFunctions;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.container.DockerAttributes;
import brooklyn.entity.container.DockerUtils;
import brooklyn.entity.group.Cluster;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.machine.MachineEntityImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.function.FunctionPollConfig;
import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.basic.BasicLocationDefinition;
import brooklyn.location.basic.Machines;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.docker.DockerHostLocation;
import brooklyn.location.docker.DockerLocation;
import brooklyn.location.docker.DockerResolver;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsLocationConfig;
import brooklyn.location.jclouds.networking.JcloudsLocationSecurityGroupCustomizer;
import brooklyn.location.jclouds.templates.PortableTemplateBuilder;
import brooklyn.management.LocationManager;
import brooklyn.networking.portforwarding.DockerPortForwarder;
import brooklyn.networking.sdn.SdnAgent;
import brooklyn.networking.sdn.SdnAttributes;
import brooklyn.networking.sdn.ibm.SdnVeNetwork;
import brooklyn.networking.subnet.SubnetTier;
import brooklyn.networking.subnet.SubnetTierImpl;
import brooklyn.policy.PolicySpec;
import brooklyn.policy.ha.ServiceFailureDetector;
import brooklyn.policy.ha.ServiceReplacer;
import brooklyn.policy.ha.ServiceRestarter;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.QuorumCheck.QuorumChecks;
import brooklyn.util.guava.Maybe;
import brooklyn.util.net.Cidr;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.StringPredicates;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;

import com.google.common.base.CharMatcher;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

/**
 * The host running the Docker service.
 */
public class DockerHostImpl extends MachineEntityImpl implements DockerHost {

    private static final Logger LOG = LoggerFactory.getLogger(DockerHost.class);

    private transient FunctionFeed scan;

    @Override
    public void init() {
        LOG.info("Starting Docker host id {}", getId());
        super.init();

        AtomicInteger counter = getConfig(DOCKER_INFRASTRUCTURE).getAttribute(DockerInfrastructure.DOCKER_HOST_COUNTER);
        String dockerHostName = String.format(getConfig(DockerHost.DOCKER_HOST_NAME_FORMAT), getId(), counter.incrementAndGet());
        setDisplayName(dockerHostName);
        setAttribute(DOCKER_HOST_NAME, dockerHostName);
        String repository = getConfig(DOCKER_REPOSITORY);
        if (Strings.isBlank(repository)) {
            repository = getId();
        }
        repository = DockerUtils.allowed(repository);
        setAttribute(DOCKER_REPOSITORY, repository);

        // Set a password for this host's containers
        String password = getConfig(DOCKER_PASSWORD);
        if (Strings.isBlank(password)) {
            password = Identifiers.makeRandomId(8);
            setConfig(DOCKER_PASSWORD, password);
        }

        ConfigToAttributes.apply(this, DOCKER_INFRASTRUCTURE);

        EntitySpec<?> dockerContainerSpec = EntitySpec.create(getConfig(DOCKER_CONTAINER_SPEC))
                .configure(DockerContainer.DOCKER_HOST, this)
                .configure(DockerContainer.DOCKER_INFRASTRUCTURE, getInfrastructure());
        if (getConfig(DockerInfrastructure.HA_POLICY_ENABLE)) {
            dockerContainerSpec.policy(PolicySpec.create(ServiceRestarter.class)
                    .configure(ServiceRestarter.FAILURE_SENSOR_TO_MONITOR, ServiceFailureDetector.ENTITY_FAILED));
        }
        setAttribute(DOCKER_CONTAINER_SPEC, dockerContainerSpec);

        DynamicCluster containers = addChild(EntitySpec.create(DynamicCluster.class)
                .configure(Cluster.INITIAL_SIZE, 0)
                .configure(DynamicCluster.QUARANTINE_FAILED_ENTITIES, false)
                .configure(DynamicCluster.MEMBER_SPEC, dockerContainerSpec)
                .configure(DynamicCluster.RUNNING_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty())
                .configure(DynamicCluster.UP_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty())
                .displayName("Docker Containers"));
        if (getConfig(DockerInfrastructure.HA_POLICY_ENABLE)) {
            containers.addPolicy(PolicySpec.create(ServiceReplacer.class)
                    .configure(ServiceReplacer.FAILURE_SENSOR_TO_MONITOR, ServiceRestarter.ENTITY_RESTART_FAILED));
        }
        setAttribute(DOCKER_CONTAINER_CLUSTER, containers);

        if (Entities.isManaged(this)) Entities.manage(containers);

        addEnricher(Enrichers.builder()
                .propagating(ImmutableMap.of(DynamicCluster.GROUP_SIZE, DockerAttributes.DOCKER_CONTAINER_COUNT))
                .from(containers)
                .build());
    }

    @Override
    protected Map<String, Object> obtainProvisioningFlags(MachineProvisioningLocation location) {
        Map<String, Object> flags = super.obtainProvisioningFlags(location);
        flags.putAll(getConfig(PROVISIONING_FLAGS));

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
            Map<String, Object> existingConfigOptions = location.getAllConfig(true);
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
                } else {
                    template.osFamily(OsFamily.UBUNTU).osVersionMatches("14.04");
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
                } else {
                    flags.put(JcloudsLocationConfig.MIN_RAM.getName(), 2048);
                }
            } else {
                LOG.debug("Not modifying existing hardware configuration for {}", this);
            }

            // Configure security groups for host virtual machine
            String securityGroup = getConfig(DockerInfrastructure.SECURITY_GROUP);
            if (Strings.isNonBlank(securityGroup)) {
                if (isJcloudsLocation(location, "google-compute-engine")) {
                    flags.put("networkName", securityGroup);
                } else {
                    flags.put("securityGroups", securityGroup);
                }
            } else {
                flags.put(JcloudsLocationConfig.JCLOUDS_LOCATION_CUSTOMIZERS.getName(),
                        ImmutableList.of(JcloudsLocationSecurityGroupCustomizer.getInstance(getApplicationId())));
            }

            // Setup SoftLayer template options required for IBM SDN VE
            // TODO Move this into a callback on the SdnProvider interface
            if (isJcloudsLocation(location, SoftLayerConstants.SOFTLAYER_PROVIDER_NAME) && isSdnProvider("SdnVeNetwork")) {
                if (template == null) template = new PortableTemplateBuilder();
                template.osFamily(OsFamily.CENTOS).osVersionMatches("6").os64Bit(true);
                Integer vlanId = getAttribute(DOCKER_INFRASTRUCTURE)
                        .getAttribute(DockerInfrastructure.SDN_PROVIDER)
                        .getConfig(SdnVeNetwork.VLAN_ID);
                template.options(SoftLayerTemplateOptions.Builder
                        .diskType(Volume.Type.LOCAL.name()) // FIXME Temporary setting overriding capacity limitation on account
                        .primaryBackendNetworkComponentNetworkVlanId(vlanId)
                        .portSpeed(1000)); // TODO Make configurable
                flags.put(JcloudsLocationConfig.TEMPLATE_BUILDER.getName(), template);
            }
        }
        return flags;
    }

    private boolean isJcloudsLocation(MachineProvisioningLocation location, String providerName) {
        return location instanceof JcloudsLocation
                && ((JcloudsLocation) location).getProvider().equals(providerName);
    }

    private boolean isSdnProvider(String providerName) {
        Entity sdn = getAttribute(DOCKER_INFRASTRUCTURE).getAttribute(DockerInfrastructure.SDN_PROVIDER);
        if (sdn == null) return false;
        return sdn.getEntityType().getSimpleName().equalsIgnoreCase(providerName);
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
        return getAttribute(DOCKER_SSL_PORT);
    }

    @Override
    public String getDockerHostName() {
        return getAttribute(DOCKER_HOST_NAME);
    }

    @Override
    public List<Entity> getDockerContainerList() {
        return ImmutableList.copyOf(getDockerContainerCluster().getMembers());
    }

    @Override
    public DockerInfrastructure getInfrastructure() {
        return (DockerInfrastructure) getConfig(DOCKER_INFRASTRUCTURE);
    }

    @Override
    public String getPassword() {
        return getConfig(DOCKER_PASSWORD);
    }

    @Override
    public String getRepository() {
        return getAttribute(DOCKER_REPOSITORY);
    }

    /** {@inheritDoc} */
    @Override
    public String createSshableImage(String dockerFile, String name) {
        String imageId = getDriver().buildImage(dockerFile, name);
        LOG.debug("Successfully created image {} ({}/{})", new Object[] { imageId, getRepository(), name });
        return imageId;
    }

    @Override
    public String layerSshableImageOn(String baseImage, String tag) {
        String imageId = getDriver().layerSshableImageOn(baseImage, tag);
        LOG.debug("Successfully created SSHable image {} from {}", imageId, baseImage);
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
        if (LOG.isDebugEnabled()) LOG.debug("Successfully executed Docker {}: {}", Strings.getFirstWord(command), Strings.getFirstLine(stdout));
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
        return (DockerHostLocation) getAttribute(DYNAMIC_LOCATION);
    }

    @Override
    public boolean isLocationAvailable() {
        return getDynamicLocation() != null;
    }

    @Override
    public DynamicCluster getDockerContainerCluster() { return getAttribute(DOCKER_CONTAINER_CLUSTER); }

    @Override
    public JcloudsLocation getJcloudsLocation() { return getAttribute(JCLOUDS_DOCKER_LOCATION); }

    @Override
    public SubnetTier getSubnetTier() { return getAttribute(DOCKER_HOST_SUBNET_TIER); }

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
        setAttribute(LOCATION_SPEC, locationSpec);

        final LocationDefinition definition = new BasicLocationDefinition(locationName, locationSpec, flags);
        Location location = getManagementContext().getLocationRegistry().resolve(definition);
        setAttribute(DYNAMIC_LOCATION, location);
        setAttribute(LOCATION_NAME, location.getId());
        getManagementContext().getLocationManager().manage(location);

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

        setAttribute(DYNAMIC_LOCATION, null);
        setAttribute(LOCATION_NAME, null);
    }

    @Override
    protected void preStart() {
        getDriver().configureSecurityGroups();

        Maybe<SshMachineLocation> found = Machines.findUniqueSshMachineLocation(getLocations());
        String dockerLocationSpec = String.format("jclouds:docker:https://%s:%s",
                found.get().getSshHostAndPort().getHostText(), getDockerPort());
        String certificatePath = getConfig(DockerInfrastructure.DOCKER_CERTIFICATE_PATH);
        String keyPath = getConfig(DockerInfrastructure.DOCKER_KEY_PATH);
        JcloudsLocation jcloudsLocation = (JcloudsLocation) getManagementContext().getLocationRegistry()
                .resolve(dockerLocationSpec, MutableMap.of("identity", certificatePath, "credential", keyPath, ComputeServiceProperties.IMAGE_LOGIN_USER, "root:" + getPassword()));
        setAttribute(JCLOUDS_DOCKER_LOCATION, jcloudsLocation);

        DockerPortForwarder portForwarder = new DockerPortForwarder();
        portForwarder.injectManagementContext(getManagementContext());
        portForwarder.init(URI.create(jcloudsLocation.getEndpoint()));
        SubnetTier subnetTier = addChild(EntitySpec.create(SubnetTier.class, SubnetTierImpl.class)
                .configure(SubnetTier.PORT_FORWARDER, portForwarder)
                .configure(SubnetTier.SUBNET_CIDR, Cidr.UNIVERSAL));
        Entities.manage(subnetTier);
        subnetTier.start(ImmutableList.of(found.get()));
        setAttribute(DOCKER_HOST_SUBNET_TIER, subnetTier);

        Map<String, ?> flags = MutableMap.<String, Object>builder()
                .putAll(getConfig(LOCATION_FLAGS))
                .put("machine", found.get())
                .put("jcloudsLocation", jcloudsLocation)
                .put("portForwarder", portForwarder)
                .put("repository", getRepository())
                .build();
        createLocation(flags);
    }

    @Override
    public void postStart() {
        Entities.deproxy(getAttribute(DOCKER_CONTAINER_CLUSTER)).setAttribute(SERVICE_UP, Boolean.TRUE);

        if (Boolean.TRUE.equals(getAttribute(DOCKER_INFRASTRUCTURE).getConfig(SdnAttributes.SDN_ENABLE))) {
            LOG.info("Waiting on SDN agent");
            SdnAgent agent = Entities.attributeSupplierWhenReady(this, SdnAgent.SDN_AGENT).get();
            Entities.waitForServiceUp(agent);
            LOG.info("SDN agent running: " + agent.getAttribute(SERVICE_UP));
        }

        String imageId = getConfig(DOCKER_IMAGE_ID);

        if (Strings.isBlank(imageId)) {
            String dockerfileUrl = getConfig(DockerInfrastructure.DOCKERFILE_URL);
            String imageName = DockerUtils.imageName(this, dockerfileUrl, getRepository());
            imageId = createSshableImage(dockerfileUrl, imageName);
            setAttribute(DOCKER_IMAGE_NAME, imageName);
        }

        setAttribute(DOCKER_IMAGE_ID, imageId);

        scan = scanner();
    }

    private FunctionFeed scanner() {
        Duration interval = getConfig(SCAN_INTERVAL);
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

        SdnAgent agent = getAttribute(SdnAgent.SDN_AGENT);
        if (agent != null) {
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
        // TODO remember that _half started_ containers left behind are not to be re-used
        // TODO add cleanup for these?
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
                    EntitySpec<DockerContainer> containerSpec = EntitySpec.create(getConfig(DOCKER_CONTAINER_SPEC))
                            .configure(SoftwareProcess.ENTITY_STARTED, Boolean.TRUE)
                            .configure(DockerContainer.DOCKER_HOST, this)
                            .configure(DockerContainer.DOCKER_INFRASTRUCTURE, getInfrastructure())
                            .configure(DockerContainer.DOCKER_IMAGE_ID, imageId)
                            .configure(DockerAttributes.DOCKER_IMAGE_NAME, imageName)
                            .configure(DockerContainer.LOCATION_FLAGS, MutableMap.of("container", getMachine()));

                    // Create, manage and start the container
                    DockerContainer added = getDockerContainerCluster().addChild(containerSpec);
                    Entities.manage(added);
                    getDockerContainerCluster().addMember(added);
                    ((EntityLocal) added).setAttribute(DockerContainer.CONTAINER_ID, containerId);
                    added.start(ImmutableList.of(getDynamicLocation().getMachine()));
                }
            }
        } finally {
            getDynamicLocation().getLock().unlock();
        }
    }

    static {
        RendererHints.register(DOCKER_INFRASTRUCTURE, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(DOCKER_CONTAINER_CLUSTER, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
    }

}
