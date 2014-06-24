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
package brooklyn.entity.container.docker;

import io.cloudsoft.networking.portforwarding.DockerPortForwarder;
import io.cloudsoft.networking.subnet.PortForwarder;
import io.cloudsoft.networking.subnet.SubnetTier;
import io.cloudsoft.networking.subnet.SubnetTierImpl;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.jclouds.compute.domain.OsFamily;
import org.jclouds.compute.domain.TemplateBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.Enrichers;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.group.Cluster;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.machine.MachineEntityImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.access.PortForwardManagerAuthority;
import brooklyn.location.basic.BasicLocationDefinition;
import brooklyn.location.basic.Machines;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.docker.DockerHostLocation;
import brooklyn.location.docker.DockerLocation;
import brooklyn.location.docker.DockerResolver;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsLocationConfig;
import brooklyn.location.jclouds.templates.PortableTemplateBuilder;
import brooklyn.management.LocationManager;
import brooklyn.management.ManagementContext;
import brooklyn.policy.PolicySpec;
import brooklyn.policy.ha.ServiceFailureDetector;
import brooklyn.policy.ha.ServiceReplacer;
import brooklyn.policy.ha.ServiceRestarter;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.guava.Maybe;
import brooklyn.util.net.Cidr;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * The host running the Docker service.
 */
public class DockerHostImpl extends MachineEntityImpl implements DockerHost {

    private static final Logger LOG = LoggerFactory.getLogger(DockerHostImpl.class);
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private DynamicCluster containers;
    private JcloudsLocation jcloudsLocation;
    private DockerPortForwarder portForwarder;
    private SubnetTier subnetTier;

    @Override
    public void init() {
        LOG.info("Starting Docker host id {}", getId());

        String dockerHostName = String.format(getConfig(DockerHost.HOST_NAME_FORMAT), getId(), COUNTER.incrementAndGet());
        setDisplayName(dockerHostName);
        setAttribute(HOST_NAME, dockerHostName);

        EntitySpec<?> dockerContainerSpec = EntitySpec.create(getConfig(DOCKER_CONTAINER_SPEC))
                .configure(DockerContainer.DOCKER_HOST, this);
        if (getConfig(HA_POLICY_ENABLE)) {
            dockerContainerSpec.policy(PolicySpec.create(ServiceFailureDetector.class));
            dockerContainerSpec.policy(PolicySpec.create(ServiceRestarter.class)
                    .configure(ServiceRestarter.FAILURE_SENSOR_TO_MONITOR, ServiceFailureDetector.ENTITY_FAILED));
        }

        containers = addChild(EntitySpec.create(DynamicCluster.class)
                .configure(Cluster.INITIAL_SIZE, 0)
                .configure(DynamicCluster.QUARANTINE_FAILED_ENTITIES, false)
                .configure(DynamicCluster.MEMBER_SPEC, dockerContainerSpec)
                .displayName("Docker Containers"));
        if (getConfig(HA_POLICY_ENABLE)) {
            containers.addPolicy(PolicySpec.create(ServiceReplacer.class)
                    .configure(ServiceReplacer.FAILURE_SENSOR_TO_MONITOR, ServiceRestarter.ENTITY_RESTART_FAILED));
        }

        if (Entities.isManaged(this)) Entities.manage(containers);

        addEnricher(Enrichers.builder()
                .propagating(ImmutableMap.of(DynamicCluster.GROUP_SIZE, DockerAttributes.DOCKER_CONTAINER_COUNT))
                .from(containers)
                .build());
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();
    }

    @Override
    protected Map<String, Object> obtainProvisioningFlags(MachineProvisioningLocation location) {
        Map<String, Object> flags = super.obtainProvisioningFlags(location);

        // Configure template for host virtual machine
        TemplateBuilder template = (TemplateBuilder) flags.get(JcloudsLocationConfig.TEMPLATE_BUILDER.getName());
        if (template == null) {
            template = new PortableTemplateBuilder();
            if (isJcloudsLocation(location, "google-compute-engine")) {
                template.osFamily(OsFamily.CENTOS).osVersionMatches("6");
            } else {
                template.osFamily(OsFamily.UBUNTU).osVersionMatches("12.04");
            }
        }
        template.os64Bit(true);
        template.minRam(2048); // TODO from configuration
        flags.put(JcloudsLocationConfig.TEMPLATE_BUILDER.getName(), template);

        // Configure security groups for host virtual machine
        String securityGroup = getConfig(DockerInfrastructure.SECURITY_GROUP);
        if (securityGroup != null) {
            if (isJcloudsLocation(location, "google-compute-engine")) {
                flags.put("networkName", securityGroup);
            } else {
                flags.put("securityGroups", securityGroup);
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
        Integer maxSize = getConfig(DOCKER_CONTAINER_CLUSTER_MAX_SIZE);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Resize Docker host to {} (max {}) at {}", new Object[] { desiredSize, maxSize, getLocations() });
        }
        if (desiredSize > maxSize) {
            return getDockerContainerCluster().resize(maxSize);
        } else {
            return getDockerContainerCluster().resize(desiredSize);
        }
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
        return getAttribute(DOCKER_PORT);
    }

    @Override
    public String getDockerHostName() {
        return getAttribute(HOST_NAME);
    }

    @Override
    public List<Entity> getDockerContainerList() {
        return ImmutableList.copyOf(containers.getMembers());
    }

    @Override
    public DockerInfrastructure getInfrastructure() {
        return getConfig(DOCKER_INFRASTRUCTURE);
    }

    /** {@inheritDoc} */
    @Override
    public String createSshableImage(String dockerFile, String name) {
       String imageId = getDriver().buildImage(dockerFile, name);
       if (LOG.isDebugEnabled()) LOG.debug("Successfully created image {} (brooklyn/{})", imageId, name);
       return imageId;
    }

    /** {@inheritDoc} */
    @Override
    public String runDockerCommand(String command) {
       String stdout = execCommand("docker " + command);
       if (LOG.isDebugEnabled()) LOG.debug("Successfully executed Docker {}: {}", Strings.getFirstWord(command), Strings.getFirstLine(stdout));
       return stdout;
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
    public DynamicCluster getDockerContainerCluster() { return containers; }

    @Override
    public JcloudsLocation getJcloudsLocation() { return jcloudsLocation; }

    @Override
    public PortForwarder getPortForwarder() { return portForwarder; }

    @Override
    public SubnetTier getSubnetTier() { return subnetTier; }

    /**
     * Create a new {@link DockerHostLocation} wrapping the machine we are starting in.
     */
    @Override
    public DockerHostLocation createLocation(Map<String, ?> flags) {
        DockerInfrastructure infrastructure = getConfig(DOCKER_INFRASTRUCTURE);
        DockerLocation docker = infrastructure.getDynamicLocation();
        String locationName = docker.getId() + "-" + getDockerHostName();

        String locationSpec = String.format(DockerResolver.DOCKER_HOST_MACHINE_SPEC, infrastructure.getId(), getId()) + String.format(":(name=\"%s\")", locationName);
        setAttribute(LOCATION_SPEC, locationSpec);

        final LocationDefinition definition = new BasicLocationDefinition(locationName, locationSpec, flags);
        Location location = getManagementContext().getLocationRegistry().resolve(definition);
        setAttribute(DYNAMIC_LOCATION, location);
        setAttribute(LOCATION_NAME, location.getId());

        if (getConfig(DockerInfrastructure.REGISTER_DOCKER_HOST_LOCATIONS)) {
            getManagementContext().getLocationRegistry().updateDefinedLocation(definition);
        }
        getManagementContext().getLocationManager().manage(location);

        getManagementContext().addPropertiesReloadListener(new ManagementContext.PropertiesReloadListener() {
            @Override
            public void reloaded() {
                Location resolved = getManagementContext().getLocationRegistry().resolve(definition);
                if (getConfig(DockerInfrastructure.REGISTER_DOCKER_HOST_LOCATIONS)) {
                    getManagementContext().getLocationRegistry().updateDefinedLocation(definition);
                }
                getManagementContext().getLocationManager().manage(resolved);
            }
        });

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
            if (getConfig(DockerInfrastructure.REGISTER_DOCKER_HOST_LOCATIONS)) {
                getManagementContext().getLocationRegistry().removeDefinedLocation(location.getId());
            }
        }

        setAttribute(DYNAMIC_LOCATION, null);
        setAttribute(LOCATION_NAME, null);
    }

    @Override
    protected void preStart() {
        Maybe<SshMachineLocation> found = Machines.findUniqueSshMachineLocation(getLocations());
        String dockerLocationSpec = String.format("jclouds:docker:http://%s:%s",
                found.get().getSshHostAndPort().getHostText(), getDockerPort());
        jcloudsLocation = (JcloudsLocation) getManagementContext().getLocationRegistry()
                .resolve(dockerLocationSpec, MutableMap.of("identity", "docker", "credential", "docker"));

        portForwarder = new DockerPortForwarder(new PortForwardManagerAuthority(this));
        portForwarder.init(URI.create(jcloudsLocation.getEndpoint()));

        subnetTier = addChild(EntitySpec.create(SubnetTier.class, SubnetTierImpl.class)
                .configure(SubnetTier.PORT_FORWARDER, portForwarder)
                .configure(SubnetTier.SUBNET_CIDR, Cidr.UNIVERSAL));
        subnetTier.start(ImmutableList.of(found.get()));

        Map<String, ?> flags = MutableMap.<String, Object>builder()
                .putAll(getConfig(LOCATION_FLAGS))
                .put("machine", found.get())
                .put("jcloudsLocation", jcloudsLocation)
                .put("portForwarder", portForwarder)
                .build();
        createLocation(flags);
    }

    @Override
    public void postStart() {
        String imageId = getConfig(DOCKER_IMAGE_ID);

        if (Strings.isBlank(imageId)) {
            String dockerfileUrl = getConfig(DockerInfrastructure.DOCKERFILE_URL);
            imageId = createSshableImage(dockerfileUrl, "default");
        }

        setAttribute(DOCKER_IMAGE_ID, imageId);
    }

    @Override
    public void doStop() {
        super.doStop();

        deleteLocation();
    }

}
