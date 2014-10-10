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
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.render.RendererHints;
import brooklyn.enricher.Enrichers;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.basic.BasicStartableImpl;
import brooklyn.entity.basic.DelegateEntity;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.basic.SoftwareProcess.ChildStartableMode;
import brooklyn.entity.container.DockerAttributes;
import brooklyn.entity.container.weave.WeaveInfrastructure;
import brooklyn.entity.group.Cluster;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.group.DynamicMultiGroup;
import brooklyn.entity.machine.MachineAttributes;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.basic.BasicLocationDefinition;
import brooklyn.location.docker.DockerContainerLocation;
import brooklyn.location.docker.DockerLocation;
import brooklyn.location.docker.DockerResolver;
import brooklyn.management.LocationManager;
import brooklyn.management.ManagementContext;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.time.Duration;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class DockerInfrastructureImpl extends BasicStartableImpl implements DockerInfrastructure {

    static {
        DockerAttributes.init();

        RendererHints.register(DOCKER_HOST_CLUSTER, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(DOCKER_CONTAINER_FABRIC, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(DOCKER_APPLICATIONS, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(WEAVE_INFRASTRUCTURE, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
    }

    private static final Logger LOG = LoggerFactory.getLogger(DockerInfrastructureImpl.class);

    private transient final AtomicBoolean started = new AtomicBoolean(false);

    private Predicate<Entity> sameInfrastructure = new Predicate<Entity>() {
        @Override
        public boolean apply(@Nullable Entity input) {
            // Check if entity is deployed to a DockerContainerLocation
            Optional<Location> lookup = Iterables.tryFind(input.getLocations(), Predicates.instanceOf(DockerContainerLocation.class));
            if (lookup.isPresent()) {
                DockerContainerLocation container = (DockerContainerLocation) lookup.get();
                // Only containers that are part of this infrastructure
                return getId().equals(container.getOwner().getDockerHost().getInfrastructure().getId());
            } else {
                return false;
            }
        }
    };

    @Override
    public void init() {
        LOG.info("Starting Docker infrastructure id {}", getId());
        super.init();

        int initialSize = getConfig(DOCKER_HOST_CLUSTER_MIN_SIZE);
        EntitySpec<?> dockerHostSpec = EntitySpec.create(getConfig(DOCKER_HOST_SPEC))
                .configure(DockerHost.DOCKER_INFRASTRUCTURE, this)
                .configure(SoftwareProcess.SUGGESTED_VERSION, getConfig(DOCKER_VERSION))
                .configure(SoftwareProcess.CHILDREN_STARTABLE_MODE, ChildStartableMode.BACKGROUND_LATE);

        DynamicCluster hosts = addChild(EntitySpec.create(DynamicCluster.class)
                .configure(Cluster.INITIAL_SIZE, initialSize)
                .configure(DynamicCluster.QUARANTINE_FAILED_ENTITIES, true)
                .configure(DynamicCluster.MEMBER_SPEC, dockerHostSpec)
                .displayName("Docker Hosts"));

        DynamicGroup fabric = addChild(EntitySpec.create(DynamicGroup.class)
                .configure(DynamicGroup.ENTITY_FILTER, Predicates.and(Predicates.instanceOf(DockerContainer.class), EntityPredicates.attributeEqualTo(DockerContainer.DOCKER_INFRASTRUCTURE, this)))
                .configure(DynamicGroup.MEMBER_DELEGATE_CHILDREN, true)
                .displayName("All Docker Containers"));

        DynamicMultiGroup buckets = addChild(EntitySpec.create(DynamicMultiGroup.class)
                .configure(DynamicMultiGroup.ENTITY_FILTER, sameInfrastructure)
                .configure(DynamicMultiGroup.RESCAN_INTERVAL, 15L)
                .configure(DynamicMultiGroup.BUCKET_FUNCTION, new Function<Entity, String>() {
                        @Override
                        public String apply(@Nullable Entity input) {
                            return input.getApplication().getDisplayName();
                        }
                    })
                .configure(DynamicMultiGroup.BUCKET_SPEC, EntitySpec.create(BasicGroup.class)
                        .configure(BasicGroup.MEMBER_DELEGATE_CHILDREN, true))
                .displayName("Docker Applications"));

        if (getConfig(WEAVE_ENABLED)) {
            WeaveInfrastructure weave = addChild(EntitySpec.create(WeaveInfrastructure.class)
                    .configure(WeaveInfrastructure.DOCKER_INFRASTRUCTURE, this));
            setAttribute(WEAVE_INFRASTRUCTURE, weave);

            if (Entities.isManaged(this)) {
                Entities.manage(weave);
            }
        }

        if (Entities.isManaged(this)) {
            Entities.manage(hosts);
            Entities.manage(fabric);
            Entities.manage(buckets);
        }
        setAttribute(DOCKER_HOST_CLUSTER, hosts);
        setAttribute(DOCKER_CONTAINER_FABRIC, fabric);
        setAttribute(DOCKER_APPLICATIONS, buckets);

        hosts.addEnricher(Enrichers.builder()
                .aggregating(DockerHost.CPU_USAGE)
                .computingAverage()
                .fromMembers()
                .publishing(MachineAttributes.AVERAGE_CPU_USAGE)
                .valueToReportIfNoSensors(0d)
                .build());
        hosts.addEnricher(Enrichers.builder()
                .aggregating(DOCKER_CONTAINER_COUNT)
                .computingSum()
                .fromMembers()
                .publishing(DOCKER_CONTAINER_COUNT)
                .build());

        addEnricher(Enrichers.builder()
                .propagating(DOCKER_CONTAINER_COUNT, MachineAttributes.AVERAGE_CPU_USAGE)
                .from(hosts)
                .build());
        addEnricher(Enrichers.builder()
                .propagating(ImmutableMap.of(DynamicCluster.GROUP_SIZE, DOCKER_HOST_COUNT))
                .from(hosts)
                .build());
    }

    @Override
    public List<Entity> getDockerHostList() {
        if (getDockerHostCluster() == null) {
            return ImmutableList.of();
        } else {
            return ImmutableList.copyOf(getDockerHostCluster().getMembers());
        }
    }

    @Override
    public DynamicCluster getDockerHostCluster() { return getAttribute(DOCKER_HOST_CLUSTER); }

    @Override
    public List<Entity> getDockerContainerList() {
        if (getContainerFabric() == null) {
            return ImmutableList.of();
        } else {
            return ImmutableList.copyOf(getContainerFabric().getMembers());
        }
    }

    @Override
    public DynamicGroup getContainerFabric() { return getAttribute(DOCKER_CONTAINER_FABRIC); }

    @Override
    public Integer resize(Integer desiredSize) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Resize Docker infrastructure to {} at {}", new Object[] { desiredSize, getLocations() });
        }
        return getDockerHostCluster().resize(desiredSize);
    }

    @Override
    public Integer getCurrentSize() {
        return getDockerHostCluster().getCurrentSize();
    }

    @Override
    public DockerLocation getDynamicLocation() {
        return (DockerLocation) getAttribute(DYNAMIC_LOCATION);
    }

    @Override
    public boolean isLocationAvailable() {
        return started.get() && getDynamicLocation() != null;
    }

    @Override
    public DockerLocation createLocation(Map<String, ?> flags) {
        String locationName = getConfig(LOCATION_NAME);
        if (locationName == null) {
            String prefix = getConfig(LOCATION_NAME_PREFIX);
            String suffix = getConfig(LOCATION_NAME_SUFFIX);
            locationName = Joiner.on("-").skipNulls().join(prefix, getId(), suffix);
        }
        String locationSpec = String.format(DockerResolver.DOCKER_INFRASTRUCTURE_SPEC, getId()) + String.format(":(name=\"%s\")", locationName);
        setAttribute(LOCATION_SPEC, locationSpec);

        final LocationDefinition definition = new BasicLocationDefinition(locationName, locationSpec, flags);
        Location location = getManagementContext().getLocationRegistry().resolve(definition);
        getManagementContext().getLocationRegistry().updateDefinedLocation(definition);
        getManagementContext().getLocationManager().manage(location);

        getManagementContext().addPropertiesReloadListener(new ManagementContext.PropertiesReloadListener() {
            @Override
            public void reloaded() {
                if (started.get()) {
                    Location resolved = getManagementContext().getLocationRegistry().resolve(definition);
                    getManagementContext().getLocationRegistry().updateDefinedLocation(definition);
                    getManagementContext().getLocationManager().manage(resolved);
                }
            }
        });

        setAttribute(DYNAMIC_LOCATION, location);
        setAttribute(LOCATION_NAME, location.getId());

        LOG.info("New Docker location {} created", location);
        return (DockerLocation) location;
    }

    @Override
    public void deleteLocation() {
        DockerLocation location = getDynamicLocation();

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
    public void start(Collection<? extends Location> locations) {
        if (started.compareAndSet(false, true)) {
            // TODO support multiple locations
            setAttribute(SERVICE_UP, Boolean.FALSE);

            Location provisioner = Iterables.getOnlyElement(locations);
            LOG.info("Creating new DockerLocation wrapping {}", provisioner);

            Map<String, ?> flags = MutableMap.<String, Object>builder()
                    .putAll(getConfig(LOCATION_FLAGS))
                    .put("provisioner", provisioner)
                    .putIfNotNull("strategies", getConfig(PLACEMENT_STRATEGIES))
                    .build();
            createLocation(flags);

            super.start(locations);

            setAttribute(SERVICE_UP, Boolean.TRUE);
        }
    }

    /**
     * De-register our {@link DockerLocation} and its children.
     */
    @Override
    public void stop() {
        if (started.compareAndSet(true, false)) {
            setAttribute(SERVICE_UP, Boolean.FALSE);

            // Find all applications and stop, blocking for up to five minutes until ended
            try {
                Iterable<Entity> entities = Iterables.filter(getManagementContext().getEntityManager().getEntities(), sameInfrastructure);
                Set<Application> applications = ImmutableSet.copyOf(Iterables.transform(entities, new Function<Entity, Application>() {
                    @Override
                    public Application apply(Entity input) { return input.getApplication(); }
                }));
                Entities.invokeEffectorList(this, applications, Startable.STOP)
                        .get(Duration.FIVE_MINUTES);
            } catch (Exception e) {
                LOG.warn("Error stopping applications: {}", e);
            }

            super.stop();

            deleteLocation();
        }
    }

}
