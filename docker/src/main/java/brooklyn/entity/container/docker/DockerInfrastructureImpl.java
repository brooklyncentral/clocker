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

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import brooklyn.enricher.Enrichers;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BasicStartableImpl;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.basic.SoftwareProcess.ChildStartableMode;
import brooklyn.entity.group.Cluster;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.java.UsesJmx.JmxAgentModes;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.basic.BasicLocationDefinition;
import brooklyn.location.docker.DockerLocation;
import brooklyn.location.docker.DockerResolver;
import brooklyn.management.LocationManager;
import brooklyn.util.collections.MutableMap;

public class DockerInfrastructureImpl extends BasicStartableImpl implements DockerInfrastructure {

    private static final Logger log = LoggerFactory.getLogger(DockerInfrastructureImpl.class);

    private DynamicCluster dockerHosts;
    private DynamicGroup fabric;
    private DockerLocation docker;


    @Override
    public void init() {
        int initialSize = getConfig(DOCKER_HOST_CLUSTER_MIN_SIZE);
        EntitySpec dockerHostSpec = EntitySpec.create(getConfig(DOCKER_HOST_SPEC))
                .configure(DockerHost.DOCKER_INFRASTRUCTURE, this)
                .configure(UsesJmx.USE_JMX, Boolean.TRUE)
                .configure(UsesJmx.JMX_AGENT_MODE, JmxAgentModes.JMX_RMI_CUSTOM_AGENT)
                .configure(SoftwareProcess.CHILDREN_STARTABLE_MODE, ChildStartableMode.BACKGROUND_LATE);

        dockerHosts = addChild(EntitySpec.create(DynamicCluster.class)
                .configure(Cluster.INITIAL_SIZE, initialSize)
                .configure(DynamicCluster.QUARANTINE_FAILED_ENTITIES, true)
                .configure(DynamicCluster.MEMBER_SPEC, dockerHostSpec)
                .displayName("Docker Hosts"));

        fabric = addChild(EntitySpec.create(DynamicGroup.class)
                .configure(DynamicGroup.ENTITY_FILTER, Predicates.instanceOf(DockerContainer.class))
                .displayName("All Docker Containers"));

        if (Entities.isManaged(this)) {
            Entities.manage(dockerHosts);
            Entities.manage(fabric);
        }

        dockerHosts.addEnricher(Enrichers.builder()
                .aggregating(DockerAttributes.TOTAL_HEAP_MEMORY)
                .computingSum()
                .fromMembers()
                .publishing(DockerAttributes.TOTAL_HEAP_MEMORY)
                .build());
        dockerHosts.addEnricher(Enrichers.builder()
                .aggregating(DockerAttributes.HEAP_MEMORY_DELTA_PER_SECOND_IN_WINDOW)
                .computingSum()
                .fromMembers()
                .publishing(DockerAttributes.HEAP_MEMORY_DELTA_PER_SECOND_IN_WINDOW)
                .build());
        dockerHosts.addEnricher(Enrichers.builder()
                .aggregating(DockerAttributes.AVERAGE_CPU_USAGE)
                .computingAverage()
                .fromMembers()
                .publishing(DockerAttributes.AVERAGE_CPU_USAGE)
                .build());
        dockerHosts.addEnricher(Enrichers.builder()
                .aggregating(DOCKER_CONTAINER_COUNT)
                .computingSum()
                .fromMembers()
                .publishing(DOCKER_CONTAINER_COUNT)
                .build());

        addEnricher(Enrichers.builder()
                .propagating(DockerAttributes.TOTAL_HEAP_MEMORY, DOCKER_CONTAINER_COUNT, DockerAttributes.AVERAGE_CPU_USAGE, DockerAttributes.HEAP_MEMORY_DELTA_PER_SECOND_IN_WINDOW)
                .from(dockerHosts)
                .build());
        addEnricher(Enrichers.builder()
                .propagating(ImmutableMap.of(DynamicCluster.GROUP_SIZE, DOCKER_HOST_COUNT))
                .from(dockerHosts)
                .build());
    }

    @Override
    public List<Entity> getDockerHostList() {
        if (dockerHosts == null) {
            return ImmutableList.of();
        } else {
            return ImmutableList.copyOf(dockerHosts.getMembers());
        }
    }

    @Override
    public DynamicCluster getDockerCluster() {
        return dockerHosts;
    }

    @Override
    public List<Entity> getDockerContainerList() {
        if (fabric == null) {
            return ImmutableList.of();
        } else {
            return ImmutableList.copyOf(fabric.getMembers());
        }
    }

    @Override
    public DynamicGroup getContainerFabric() { return fabric; }

    @Override
    public Integer resize(Integer desiredSize) {
        return dockerHosts.resize(desiredSize);
    }

    @Override
    public Integer getCurrentSize() {
        return dockerHosts.getCurrentSize();
    }

    @Override
    public DockerLocation getDynamicLocation() {
        return docker;
    }

    @Override
    public DockerLocation createLocation(Map<String, ?> flags) {
        String locationName = getConfig(LOCATION_NAME);
        if (locationName == null) {
            String prefix = getConfig(LOCATION_NAME_PREFIX);
            String suffix = getConfig(LOCATION_NAME_SUFFIX);
            locationName = Joiner.on("-").skipNulls().join(prefix, getId(), suffix);
        }
        String locationSpec = String.format(DockerResolver.DOCKER_INFRASTRUCTURE_SPEC,
                getId()) + String.format(":(name=\"%s\")", locationName);
        setAttribute(LOCATION_SPEC, locationSpec);
        LocationDefinition definition = new BasicLocationDefinition(locationName, locationSpec, flags);
        Location location = getManagementContext().getLocationRegistry().resolve(definition);

        setAttribute(DYNAMIC_LOCATION, location);
        setAttribute(LOCATION_NAME, location.getId());
        getManagementContext().getLocationRegistry().updateDefinedLocation(definition);

        return (DockerLocation) location;
    }

    @Override
    public boolean isLocationAvailable() {
        return docker != null;
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        Location provisioner = Iterables.getOnlyElement(locations);
        log.info("Creating new DockerLocation wrapping {}", provisioner);

        Map<String, ?> flags = MutableMap.<String, Object>builder()
                .putAll(getConfig(LOCATION_FLAGS))
                .put("provisioner", provisioner)
                .build();
        docker = createLocation(flags);
        log.info("New Docker location {} created", docker);

        super.start(locations);
    }

    /**
     * De-register our {@link DockerLocation} and its children.
     */
    public void stop() {
        super.stop();

        deleteLocation();
    }

    @Override
    public void deleteLocation() {
        LocationManager mgr = getManagementContext().getLocationManager();
        if (docker != null && mgr.isManaged(docker)) {
            mgr.unmanage(docker);
            setAttribute(DYNAMIC_LOCATION,  null);
        }
    }

}
