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

import javax.annotation.Nullable;
import javax.net.ssl.X509TrustManager;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationDefinition;
import org.apache.brooklyn.api.location.LocationRegistry;
import org.apache.brooklyn.api.mgmt.LocationManager;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.sensor.EnricherSpec;
import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.core.entity.AbstractApplication;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityPredicates;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.location.BasicLocationDefinition;
import org.apache.brooklyn.core.location.BasicLocationRegistry;
import org.apache.brooklyn.core.location.dynamic.LocationOwner;
import org.apache.brooklyn.enricher.stock.Enrichers;
import org.apache.brooklyn.entity.group.BasicGroup;
import org.apache.brooklyn.entity.group.Cluster;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.entity.group.DynamicGroup;
import org.apache.brooklyn.entity.group.DynamicMultiGroup;
import org.apache.brooklyn.entity.machine.MachineAttributes;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.software.base.SoftwareProcess.ChildStartableMode;
import org.apache.brooklyn.entity.stock.DelegateEntity;
import org.apache.brooklyn.policy.autoscaling.AutoScalerPolicy;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.QuorumCheck.QuorumChecks;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.crypto.SecureKeys;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;

import brooklyn.entity.container.DockerAttributes;
import brooklyn.entity.container.DockerUtils;
import brooklyn.entity.container.docker.registry.DockerRegistry;
import brooklyn.entity.container.policy.ContainerHeadroomEnricher;
import brooklyn.location.docker.DockerLocation;
import brooklyn.location.docker.DockerResolver;
import brooklyn.networking.sdn.SdnAttributes;

public class DockerInfrastructureImpl extends AbstractApplication implements DockerInfrastructure {

    private static final Logger LOG = LoggerFactory.getLogger(DockerInfrastructure.class);

    private transient Object mutex = new Object[0];

    @Override
    public Object getInfrastructureMutex() {
        return mutex;
    }

    @Override
    public void init() {
        LOG.info("Starting Docker infrastructure id {}", getId());
        registerLocationResolver();
        super.init();

        sensors().set(DOCKER_HOST_COUNTER, new AtomicInteger(0));
        sensors().set(DOCKER_CONTAINER_COUNTER, new AtomicInteger(0));
        sensors().set(DOCKER_IMAGE_REGISTRY, config().get(DOCKER_IMAGE_REGISTRY));
        int initialSize = config().get(DOCKER_HOST_CLUSTER_MIN_SIZE);

        Map<String, String> runtimeFiles = ImmutableMap.of();
        if (!config().get(DOCKER_GENERATE_TLS_CERTIFICATES)) {
            runtimeFiles = ImmutableMap.<String, String>builder()
                    .put(config().get(DOCKER_SERVER_CERTIFICATE_PATH), "cert.pem")
                    .put(config().get(DOCKER_SERVER_KEY_PATH), "key.pem")
                    .put(config().get(DOCKER_CA_CERTIFICATE_PATH), "ca.pem")
                    .build();
        }

        try {
            String caCertPath = config().get(DOCKER_CA_CERTIFICATE_PATH);
            try (InputStream caCert = ResourceUtils.create().getResourceFromUrl(caCertPath)) {
                X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(caCert);
                KeyStore store = SecureKeys.newKeyStore();
                store.setCertificateEntry("ca", certificate);
                X509TrustManager trustManager = SecureKeys.getTrustManager(certificate);
                // TODO incorporate this trust manager into jclouds SSL context
            }
        } catch (IOException | KeyStoreException | CertificateException e) {
            Exceptions.propagate(e);
        }

        EntitySpec<?> dockerHostSpec = EntitySpec.create(config().get(DOCKER_HOST_SPEC))
                .configure(DockerHost.DOCKER_INFRASTRUCTURE, this)
                .configure(DockerHost.RUNTIME_FILES, runtimeFiles)
                .configure(SoftwareProcess.CHILDREN_STARTABLE_MODE, ChildStartableMode.BACKGROUND_LATE);
        String dockerVersion = config().get(DOCKER_VERSION);
        if (Strings.isNonBlank(dockerVersion)) {
            dockerHostSpec.configure(SoftwareProcess.SUGGESTED_VERSION, dockerVersion);
        }
        if (Boolean.TRUE.equals(config().get(SdnAttributes.SDN_DEBUG))) {
            dockerHostSpec.configure(DockerAttributes.DOCKERFILE_URL, DockerUtils.UBUNTU_NETWORKING_DOCKERFILE);
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

        if (config().get(SDN_ENABLE) && config().get(SDN_PROVIDER_SPEC) != null) {
            Entity sdn = addChild(EntitySpec.create(config().get(SDN_PROVIDER_SPEC))
                    .configure(DockerAttributes.DOCKER_INFRASTRUCTURE, this));
            sensors().set(SDN_PROVIDER, sdn);

            if (Entities.isManaged(this)) {
                Entities.manage(sdn);
            }
        }

        if (Entities.isManaged(this)) {
            Entities.manage(hosts);
            Entities.manage(fabric);
            Entities.manage(buckets);
        }

        sensors().set(DOCKER_HOST_CLUSTER, hosts);
        sensors().set(DOCKER_CONTAINER_FABRIC, fabric);
        sensors().set(DOCKER_APPLICATIONS, buckets);

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

        Integer headroom = config().get(ContainerHeadroomEnricher.CONTAINER_HEADROOM);
        Double headroomPercent = config().get(ContainerHeadroomEnricher.CONTAINER_HEADROOM_PERCENTAGE);
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

        sensors().set(Attributes.MAIN_URI, URI.create("/clocker"));
    }

    private void registerLocationResolver() {
        // Doesn't matter if the resolver is already registered through ServiceLoader.
        // It just overwrite the existing registration (if any).
        // TODO Register separate resolvers for each infrastructure instance, unregister on unmanage.
        LocationRegistry registry = getManagementContext().getLocationRegistry();
        DockerResolver dockerResolver = new DockerResolver();
        ((BasicLocationRegistry) registry).registerResolver(dockerResolver);
        if (LOG.isDebugEnabled()) LOG.debug("Explicitly registered docker resolver: " + dockerResolver);
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
    public DynamicCluster getDockerHostCluster() {
        return sensors().get(DOCKER_HOST_CLUSTER);
    }

    @Override
    public List<Entity> getDockerContainerList() {
        if (getContainerFabric() == null) {
            return ImmutableList.of();
        } else {
            return ImmutableList.copyOf(getContainerFabric().getMembers());
        }
    }

    @Override
    public DynamicGroup getContainerFabric() {
        return sensors().get(DOCKER_CONTAINER_FABRIC);
    }

    @Override
    public Integer resize(Integer desiredSize) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Resize Docker infrastructure to {} at {}", new Object[]{desiredSize, getLocations()});
        }
        return getDockerHostCluster().resize(desiredSize);
    }

    @Override
    public Integer getCurrentSize() {
        return getDockerHostCluster().getCurrentSize();
    }

    @Override
    public DockerLocation getDynamicLocation() {
        return (DockerLocation) sensors().get(DYNAMIC_LOCATION);
    }

    @Override
    public boolean isLocationAvailable() {
        return getDynamicLocation() != null;
    }

    @Override
    public DockerLocation createLocation(Map<String, ?> flags) {
        String locationName = config().get(LOCATION_NAME);
        if (Strings.isBlank(locationName)) {
            String prefix = config().get(LOCATION_NAME_PREFIX);
            String suffix = config().get(LOCATION_NAME_SUFFIX);
            locationName = Joiner.on("-").skipNulls().join(prefix, getId(), suffix);
        }
        LocationDefinition check = getManagementContext().getLocationRegistry().getDefinedLocationByName(locationName);
        if (check != null) {
            throw new IllegalStateException("Location " + locationName + " is already defined: " + check);
        }

        String locationSpec = String.format(DockerResolver.DOCKER_INFRASTRUCTURE_SPEC, getId()) + String.format(":(name=\"%s\")", locationName);
        sensors().set(LOCATION_SPEC, locationSpec);
        LocationDefinition definition = new BasicLocationDefinition(locationName, locationSpec, flags);
        Location location = getManagementContext().getLocationRegistry().resolve(definition);
        getManagementContext().getLocationRegistry().updateDefinedLocation(definition);
        getManagementContext().getLocationManager().manage(location);

        ManagementContext.PropertiesReloadListener listener = DockerUtils.reloadLocationListener(getManagementContext(), definition);
        getManagementContext().addPropertiesReloadListener(listener);
        sensors().set(Attributes.PROPERTIES_RELOAD_LISTENER, listener);

        sensors().set(LocationOwner.LOCATION_DEFINITION, definition);
        sensors().set(LocationOwner.DYNAMIC_LOCATION, location);
        sensors().set(LocationOwner.LOCATION_NAME, location.getId());

        LOG.info("New Docker location {} created", location);
        return (DockerLocation) location;
    }

    @Override
    public void rebind() {
        super.rebind();

        // Reload our location definition on rebind
        ManagementContext.PropertiesReloadListener listener = sensors().get(Attributes.PROPERTIES_RELOAD_LISTENER);
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
            final LocationDefinition definition = sensors().get(LocationOwner.LOCATION_DEFINITION);
            if (definition != null) {
                getManagementContext().getLocationRegistry().removeDefinedLocation(definition.getId());
            }
        }
        ManagementContext.PropertiesReloadListener listener = sensors().get(Attributes.PROPERTIES_RELOAD_LISTENER);
        if (listener != null) {
            getManagementContext().removePropertiesReloadListener(listener);
        }

        sensors().set(LocationOwner.LOCATION_DEFINITION, null);
        sensors().set(LocationOwner.DYNAMIC_LOCATION, null);
        sensors().set(LocationOwner.LOCATION_NAME, null);
    }

    @Override
    public void doStart(Collection<? extends Location> locations) {
        // TODO support multiple locations
        sensors().set(SERVICE_UP, Boolean.FALSE);

        Location provisioner = Iterables.getOnlyElement(locations);
        LOG.info("Creating new DockerLocation wrapping {}", provisioner);

        Map<String, ?> flags = MutableMap.<String, Object>builder()
                .putAll(config().get(LOCATION_FLAGS))
                .put("provisioner", provisioner)
                .putIfNotNull("strategies", config().get(PLACEMENT_STRATEGIES))
                .build();
        createLocation(flags);

        super.doStart(locations);

        sensors().set(SERVICE_UP, Boolean.TRUE);
    }

    /**
     * De-register our {@link DockerLocation} and its children.
     */
    @Override
    public void stop() {
        sensors().set(SERVICE_UP, Boolean.FALSE);
        Duration timeout = config().get(SHUTDOWN_TIMEOUT);

        // Find all applications and stop, blocking for up to five minutes until ended
        try {
            Iterable<Entity> entities = Iterables.filter(getManagementContext().getEntityManager().getEntities(), DockerUtils.sameInfrastructure(this));
            Set<Application> applications = ImmutableSet.copyOf(Iterables.transform(entities, new Function<Entity, Application>() {
                @Override
                public Application apply(Entity input) {
                    return input.getApplication();
                }
            }));
            LOG.debug("Stopping applications: {}", Iterables.toString(applications));
            Entities.invokeEffectorList(this, applications, Startable.STOP).get(timeout);
        } catch (Exception e) {
            LOG.warn("Error stopping applications", e);
        }

        // Shutdown SDN if configured
        if (config().get(SDN_ENABLE)) {
            try {
                Entity sdn = sensors().get(SDN_PROVIDER);
                LOG.debug("Stopping SDN: {}", sdn);
                Entities.invokeEffector(this, sdn, Startable.STOP).get(timeout);
            } catch (Exception e) {
                LOG.warn("Error stopping SDN", e);
            }
        }

        // Stop all Docker hosts in parallel
        try {
            DynamicCluster hosts = sensors().get(DOCKER_HOST_CLUSTER);
            LOG.debug("Stopping hosts: {}", Iterables.toString(hosts.getMembers()));
            Entities.invokeEffectorList(this, hosts.getMembers(), Startable.STOP).get(timeout);
        } catch (Exception e) {
            LOG.warn("Error stopping hosts", e);
        }

        // Stop anything else left over
        super.stop();

        deleteLocation();
    }

    @Override
    public void postStart(Collection<? extends Location> locations) {
        super.postStart(locations);

        if (config().get(DOCKER_SHOULD_START_REGISTRY)) {
            DockerHost firstEntity = (DockerHost) sensors().get(DOCKER_HOST_CLUSTER).sensors().get(DynamicCluster.FIRST);

            EntitySpec<DockerRegistry> spec = EntitySpec.create(DockerRegistry.class)
                    .configure(DockerRegistry.DOCKER_HOST, firstEntity);

            //mount volume with images stored on it
            DockerRegistry dockerRegistry = addChild(spec);
            Entities.manage(dockerRegistry);

            LOG.debug("Starting a new Docker Registry with spec {}", spec);
            Entities.start(dockerRegistry, ImmutableList.of(firstEntity.getDynamicLocation()));

            String dockerRegistryUrl = String.format("%s:%d", dockerRegistry.sensors().get(Attributes.HOSTNAME), dockerRegistry.config().get(DockerRegistry.DOCKER_REGISTRY_PORT));
            LOG.debug("Started new docker registry. Setting registry URL config {} to {}", DOCKER_IMAGE_REGISTRY, dockerRegistryUrl);
            sensors().set(DOCKER_IMAGE_REGISTRY, dockerRegistryUrl);
        }
    }

    static {
        DockerAttributes.init();

        RendererHints.register(DOCKER_HOST_CLUSTER, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(DOCKER_CONTAINER_FABRIC, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(DOCKER_APPLICATIONS, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(SDN_PROVIDER, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
    }

}
