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

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.render.RendererHints;
import brooklyn.enricher.Enrichers;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.basic.BasicStartableImpl;
import brooklyn.entity.basic.DelegateEntity;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.basic.SoftwareProcess.ChildStartableMode;
import brooklyn.entity.container.DockerAttributes;
import brooklyn.entity.container.DockerUtils;
import brooklyn.entity.container.policy.ContainerHeadroomEnricher;
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
import brooklyn.location.docker.DockerLocation;
import brooklyn.location.docker.DockerResolver;
import brooklyn.management.LocationManager;
import brooklyn.management.ManagementContext;
import brooklyn.policy.EnricherSpec;
import brooklyn.policy.PolicySpec;
import brooklyn.policy.autoscaling.AutoScalerPolicy;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.QuorumCheck.QuorumChecks;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class DockerInfrastructureImpl extends BasicStartableImpl implements DockerInfrastructure {

    private static final Logger LOG = LoggerFactory.getLogger(DockerInfrastructureImpl.class);

    @Override
    public void init() {
        LOG.info("Starting Docker infrastructure id {}", getId());
        super.init();

        setAttribute(DOCKER_HOST_COUNTER, new AtomicInteger(0));
        setAttribute(DOCKER_CONTAINER_COUNTER, new AtomicInteger(0));

        int initialSize = getConfig(DOCKER_HOST_CLUSTER_MIN_SIZE);
        EntitySpec<?> dockerHostSpec = EntitySpec.create(getConfig(DOCKER_HOST_SPEC))
                .configure(DockerHost.DOCKER_INFRASTRUCTURE, this)
                .configure(SoftwareProcess.CHILDREN_STARTABLE_MODE, ChildStartableMode.BACKGROUND_LATE);
        String dockerVersion = getConfig(DOCKER_VERSION);
        if (Strings.isNonBlank(dockerVersion)) {
            dockerHostSpec.configure(SoftwareProcess.SUGGESTED_VERSION, dockerVersion);
        }

        DynamicCluster hosts = addChild(EntitySpec.create(DynamicCluster.class)
                .configure(Cluster.INITIAL_SIZE, initialSize)
                .configure(DynamicCluster.QUARANTINE_FAILED_ENTITIES, true)
                .configure(DynamicCluster.MEMBER_SPEC, dockerHostSpec)
                .configure(DynamicCluster.RUNNING_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty())
                .configure(DynamicCluster.UP_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty())
                .displayName("Docker Hosts"));

        DynamicGroup fabric = addChild(EntitySpec.create(DynamicGroup.class)
                .configure(DynamicGroup.ENTITY_FILTER, Predicates.and(Predicates.instanceOf(DockerContainer.class), EntityPredicates.attributeEqualTo(DockerContainer.DOCKER_INFRASTRUCTURE, this)))
                .configure(DynamicGroup.MEMBER_DELEGATE_CHILDREN, true)
                .displayName("All Docker Containers"));

        DynamicMultiGroup buckets = addChild(EntitySpec.create(DynamicMultiGroup.class)
                .configure(DynamicMultiGroup.ENTITY_FILTER, DockerUtils.sameInfrastructure(this))
                .configure(DynamicMultiGroup.RESCAN_INTERVAL, 15L)
                .configure(DynamicMultiGroup.BUCKET_FUNCTION, new Function<Entity, String>() {
                        @Override
                        public String apply(@Nullable Entity input) {
                            return input.getApplication().getDisplayName() + ":" + input.getApplicationId();
                        }
                    })
                .configure(DynamicMultiGroup.BUCKET_SPEC, EntitySpec.create(BasicGroup.class)
                        .configure(BasicGroup.MEMBER_DELEGATE_CHILDREN, true))
                .displayName("Docker Applications"));

        if (getConfig(WEAVE_ENABLED)) {
            WeaveInfrastructure weave = addChild(EntitySpec.create(WeaveInfrastructure.class)
                    .configure(WeaveInfrastructure.DOCKER_INFRASTRUCTURE, this)
                    .configure(WeaveInfrastructure.WEAVE_VERSION, getConfig(WEAVE_VERSION)));
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

        Integer headroom = getConfig(ContainerHeadroomEnricher.CONTAINER_HEADROOM);
        Double headroomPercent = getConfig(ContainerHeadroomEnricher.CONTAINER_HEADROOM_PERCENTAGE);
        if ((headroom != null && headroom > 0) || (headroomPercent != null && headroomPercent > 0d)) {
            addEnricher(EnricherSpec.create(ContainerHeadroomEnricher.class)
                    .configure(ContainerHeadroomEnricher.CONTAINER_HEADROOM, headroom)
                    .configure(ContainerHeadroomEnricher.CONTAINER_HEADROOM_PERCENTAGE, headroomPercent));
            hosts.addEnricher(Enrichers.builder()
                    .propagating(
                            ContainerHeadroomEnricher.DOCKER_CONTAINER_CLUSTER_COLD,
                            ContainerHeadroomEnricher.DOCKER_CONTAINER_CLUSTER_HOT,
                            ContainerHeadroomEnricher.DOCKER_CONTAINER_CLUSTER_OK)
                    .from(this)
                    .build());
            hosts.addPolicy(PolicySpec.create(AutoScalerPolicy.class)
                    .configure(AutoScalerPolicy.POOL_COLD_SENSOR, ContainerHeadroomEnricher.DOCKER_CONTAINER_CLUSTER_COLD)
                    .configure(AutoScalerPolicy.POOL_HOT_SENSOR, ContainerHeadroomEnricher.DOCKER_CONTAINER_CLUSTER_HOT)
                    .configure(AutoScalerPolicy.POOL_OK_SENSOR, ContainerHeadroomEnricher.DOCKER_CONTAINER_CLUSTER_OK)
                    .configure(AutoScalerPolicy.MIN_POOL_SIZE, initialSize)
                    .configure(AutoScalerPolicy.RESIZE_UP_STABILIZATION_DELAY, Duration.THIRTY_SECONDS)
                    .configure(AutoScalerPolicy.RESIZE_DOWN_STABILIZATION_DELAY, Duration.FIVE_MINUTES));
        }

        setAttribute(Attributes.MAIN_URI, URI.create("/clocker"));
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
        return getDynamicLocation() != null;
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

        ManagementContext.PropertiesReloadListener listener = DockerUtils.reloadLocationListener(getManagementContext(), definition);
        getManagementContext().addPropertiesReloadListener(listener);
        setAttribute(Attributes.PROPERTIES_RELOAD_LISTENER, listener);

        setAttribute(LOCATION_DEFINITION, definition);
        setAttribute(DYNAMIC_LOCATION, location);
        setAttribute(LOCATION_NAME, location.getId());

        LOG.info("New Docker location {} created", location);
        return (DockerLocation) location;
    }

    @Override
    public void rebind() {
        super.rebind();

        // Reload our location definition on rebind
        ManagementContext.PropertiesReloadListener listener = getAttribute(Attributes.PROPERTIES_RELOAD_LISTENER);
        if (listener != null) {
            listener.reloaded();
        }
    }

    @Override
    public void deleteLocation() {
        DockerLocation location = getDynamicLocation();

        if (location != null) {
            LocationManager mgr = getManagementContext().getLocationManager();
            if (mgr.isManaged(location)) {
                mgr.unmanage(location);
            }
            final LocationDefinition definition = getAttribute(LOCATION_DEFINITION);
            if (definition != null) {
                getManagementContext().getLocationRegistry().removeDefinedLocation(definition.getId());
            }
        }
        ManagementContext.PropertiesReloadListener listener = getAttribute(Attributes.PROPERTIES_RELOAD_LISTENER);
        if (listener != null) {
            getManagementContext().removePropertiesReloadListener(listener);
        }

        setAttribute(LOCATION_DEFINITION, null);
        setAttribute(DYNAMIC_LOCATION, null);
        setAttribute(LOCATION_NAME, null);
    }

    @Override
    public void start(Collection<? extends Location> locations) {
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

    /**
     * De-register our {@link DockerLocation} and its children.
     */
    @Override
    public void stop() {
        setAttribute(SERVICE_UP, Boolean.FALSE);

        // Find all applications and stop, blocking for up to five minutes until ended
        try {
            Iterable<Entity> entities = Iterables.filter(getManagementContext().getEntityManager().getEntities(), DockerUtils.sameInfrastructure(this));
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

        deleteLocation(); // be more resilient to errors earlier
    }

    static {
        DockerAttributes.init();

        RendererHints.register(DOCKER_HOST_CLUSTER, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(DOCKER_CONTAINER_FABRIC, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(DOCKER_APPLICATIONS, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(WEAVE_INFRASTRUCTURE, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
    }

}
