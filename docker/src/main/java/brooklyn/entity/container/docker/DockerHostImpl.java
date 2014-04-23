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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.jclouds.compute.domain.OsFamily;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import brooklyn.enricher.Enrichers;
import brooklyn.entity.Entity;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.group.Cluster;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.java.UsesJavaMXBeans;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.basic.BasicLocationDefinition;
import brooklyn.location.basic.Machines;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.docker.DockerHostLocation;
import brooklyn.location.docker.DockerLocation;
import brooklyn.location.docker.DockerResolver;
import brooklyn.location.jclouds.JcloudsLocationConfig;
import brooklyn.location.jclouds.templates.PortableTemplateBuilder;
import brooklyn.management.LocationManager;
import brooklyn.policy.PolicySpec;
import brooklyn.policy.ha.ServiceFailureDetector;
import brooklyn.policy.ha.ServiceReplacer;
import brooklyn.policy.ha.ServiceRestarter;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.guava.Maybe;

/**
 * @author Andrea Turli
 */
public class DockerHostImpl extends SoftwareProcessImpl implements DockerHost {

    private static final Logger log = LoggerFactory.getLogger(DockerHostImpl.class);
    private static final AtomicInteger counter = new AtomicInteger(0);

    private DynamicCluster containers;
    private DockerHostLocation host;

    public DockerHostImpl() {
    }

    @Override
    public void init() {
        log.info("Starting Docker host id {}", getId());

        String dockerHostName = String.format(getConfig(DockerHost.HOST_NAME_FORMAT), getId(), counter.incrementAndGet());
        setDisplayName(dockerHostName);
        setAttribute(HOST_NAME, dockerHostName);

        EntitySpec dockerContainerSpec = EntitySpec.create(getConfig(DOCKER_CONTAINER_SPEC))
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

        containers.addEnricher(Enrichers.builder()
                .aggregating(DockerAttributes.CPU_USAGE)
                .computingAverage()
                .fromMembers()
                .publishing(DockerAttributes.AVERAGE_CPU_USAGE)
                .build());

        addEnricher(Enrichers.builder()
                .propagating(ImmutableMap.of(DynamicCluster.GROUP_SIZE, DockerAttributes.DOCKER_CONTAINER_COUNT))
                .from(containers)
                .build());
        addEnricher(Enrichers.builder()
                .propagating(DockerAttributes.AVERAGE_CPU_USAGE)
                .from(containers)
                .build());
    }
    
    @Override
    public Class<?> getDriverInterface() {
        return DockerHostDriver.class;
    }

    public int getPort() {
        return checkNotNull(getAttribute(DOCKER_PORT), "%s must not be null", DOCKER_PORT);
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();
    }

    @Override
    protected void disconnectSensors() {
        disconnectServiceUpIsRunning();
        super.disconnectSensors();
    }

    @Override
    public String getShortName() {
        return "Docker Host";
    }



    @Override
    public Integer resize(@EffectorParam(name = "desiredSize", description = "The new size of the cluster") Integer desiredSize) {
        Integer maxSize = getConfig(DOCKER_CONTAINER_CLUSTER_MAX_SIZE);
        if (desiredSize > maxSize) {
            return getDockerContainerCluster().resize(maxSize);
        } else {
            return getDockerContainerCluster().resize(desiredSize);
        }
    }

    @Override
    public Integer getCurrentSize() {
        return getDockerContainerCluster().getCurrentSize();
    }

    @Override
    public String getDockerHostName() {
        return getAttribute(HOST_NAME);
    }

    @Override
    public DynamicCluster getDockerContainerCluster() {
        return containers;
    }

    @Override
    public List<Entity> getDockerContainerList() {
        return ImmutableList.copyOf(containers.getMembers());
    }

    @Override
    protected Map<String, Object> obtainProvisioningFlags(MachineProvisioningLocation location) {
        Map flags = super.obtainProvisioningFlags(location);
        flags.put(JcloudsLocationConfig.TEMPLATE_BUILDER.getName(), new PortableTemplateBuilder()
                .osFamily(OsFamily.UBUNTU)
                .osVersionMatches("12.04")
                .os64Bit(true)
                .minRam(2048));
        return flags;
    }

    @Override
    public DockerInfrastructure getInfrastructure() {
        return getConfig(DOCKER_INFRASTRUCTURE);
    }

    @Override
    public DockerHostLocation getDynamicLocation() {
        return (DockerHostLocation) getAttribute(DYNAMIC_LOCATION);
    }

    /**
     * Create a new {@link brooklyn.location.docker.DockerHostLocation} wrapping the machine we are starting in.
     */
    @Override
    public DockerHostLocation createLocation(Map<String, ?> flags) {
        DockerInfrastructure infrastructure = getConfig(DOCKER_INFRASTRUCTURE);
        DockerLocation docker = infrastructure.getDynamicLocation();
        String locationName = docker.getId() + "-" + getDockerHostName();

        String locationSpec = String.format(DockerResolver.DOCKER_HOST_MACHINE_SPEC, infrastructure.getId(),
                getId()) + String.format(":(name=\"%s\")", locationName);
        setAttribute(LOCATION_SPEC, locationSpec);
        LocationDefinition definition = new BasicLocationDefinition(locationName, locationSpec, flags);
        Location location = getManagementContext().getLocationRegistry().resolve(definition);
        setAttribute(DYNAMIC_LOCATION, location);
        setAttribute(LOCATION_NAME, location.getId());
        if (getConfig(DockerInfrastructure.REGISTER_DOCKER_HOST_LOCATIONS)) {
            getManagementContext().getLocationRegistry().updateDefinedLocation(definition);
        }
        return (DockerHostLocation) location;
    }

    @Override
    public boolean isLocationAvailable() {
        // TODO implementation
        return host != null;
    }

    @Override
    public void doStart(Collection<? extends Location> locations) {
        super.doStart(locations);

        Maybe<SshMachineLocation> found = Machines.findUniqueSshMachineLocation(getLocations());
        Map<String, ?> flags = MutableMap.<String, Object>builder()
                .putAll(getConfig(LOCATION_FLAGS))
                .put("machine", found.get())
                .build();
        host = createLocation(flags);
        log.info("New Docker host location {} created", host);
    }

    @Override
    public void doStop() {
        deleteLocation();

        super.doStop();
    }

    @Override
    public void deleteLocation() {
        LocationManager mgr = getManagementContext().getLocationManager();
        DockerHostLocation host = getDynamicLocation();
        if (host != null && mgr.isManaged(host)) {
            mgr.unmanage(host);
            setAttribute(DYNAMIC_LOCATION,  null);
        }
    }
}
