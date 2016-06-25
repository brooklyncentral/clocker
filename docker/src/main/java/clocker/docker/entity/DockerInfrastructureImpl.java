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
package clocker.docker.entity;

import io.brooklyn.entity.nosql.etcd.EtcdCluster;

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

import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clocker.docker.entity.container.DockerContainer;
import clocker.docker.entity.container.registry.DockerRegistry;
import clocker.docker.entity.util.DockerAttributes;
import clocker.docker.entity.util.DockerUtils;
import clocker.docker.location.DockerLocation;
import clocker.docker.location.DockerResolver;
import clocker.docker.networking.entity.sdn.util.SdnAttributes;
import clocker.docker.policy.ContainerHeadroomEnricher;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.net.HostAndPort;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationDefinition;
import org.apache.brooklyn.api.location.LocationRegistry;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.sensor.EnricherSpec;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampConstants;
import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.core.entity.AbstractApplication;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityPredicates;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic.ComputeServiceIndicatorsFromChildrenAndMembers;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.feed.ConfigToAttributes;
import org.apache.brooklyn.core.location.BasicLocationRegistry;
import org.apache.brooklyn.core.location.Locations;
import org.apache.brooklyn.core.location.dynamic.LocationOwner;
import org.apache.brooklyn.enricher.stock.Enrichers;
import org.apache.brooklyn.entity.group.Cluster;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.entity.group.DynamicGroup;
import org.apache.brooklyn.entity.machine.MachineAttributes;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.software.base.SoftwareProcess.ChildStartableMode;
import org.apache.brooklyn.entity.stock.DelegateEntity;
import org.apache.brooklyn.policy.autoscaling.AutoScalerPolicy;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.QuorumCheck.QuorumChecks;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.crypto.SecureKeys;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.text.VersionComparator;
import org.apache.brooklyn.util.time.Duration;

public class DockerInfrastructureImpl extends AbstractApplication implements DockerInfrastructure {

    private static final Logger LOG = LoggerFactory.getLogger(DockerInfrastructure.class);

    private transient Object mutex = new Object[0];

    @Override
    public Object getInfrastructureMutex() {
        return mutex;
    }

    @Override
    public void init() {
        String version = config().get(DOCKER_VERSION);
        if (VersionComparator.getInstance().compare("1.9", version) > 1) {
            throw new IllegalStateException("Requires libnetwork capable Docker > 1.9");
        }

        LOG.info("Starting Docker infrastructure id {}", getId());
        registerLocationResolver();
        super.init();

        int initialSize = config().get(DOCKER_HOST_CLUSTER_MIN_SIZE);

        Map<String, String> runtimeFiles = ImmutableMap.of();
        if (!config().get(DOCKER_GENERATE_TLS_CERTIFICATES)) {
            runtimeFiles = ImmutableMap.<String, String>builder()
                    .put(config().get(DOCKER_SERVER_CERTIFICATE_PATH), "cert.pem")
                    .put(config().get(DOCKER_SERVER_KEY_PATH), "key.pem")
                    .put(config().get(DOCKER_CA_CERTIFICATE_PATH), "ca.pem")
                    .build();
        }

        // Try and set the registry URL if configured and not starting local registry
        if (!config().get(DOCKER_SHOULD_START_REGISTRY)) {
            ConfigToAttributes.apply(this, DOCKER_IMAGE_REGISTRY_URL);
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

        EntitySpec<?> dockerHostSpec = EntitySpec.create(config().get(DOCKER_HOST_SPEC));
        dockerHostSpec.configure(DockerHost.DOCKER_INFRASTRUCTURE, this)
                .configure(DockerHost.RUNTIME_FILES, runtimeFiles)
                .configure(SoftwareProcess.CHILDREN_STARTABLE_MODE, ChildStartableMode.BACKGROUND_LATE);
        String dockerVersion = config().get(DOCKER_VERSION);
        if (Strings.isNonBlank(dockerVersion)) {
            dockerHostSpec.configure(SoftwareProcess.SUGGESTED_VERSION, dockerVersion);
        }
        if (Boolean.TRUE.equals(config().get(SdnAttributes.SDN_DEBUG))) {
            dockerHostSpec.configure(DockerAttributes.DOCKERFILE_URL, DockerUtils.UBUNTU_NETWORKING_DOCKERFILE);
        }
        sensors().set(DOCKER_HOST_SPEC, dockerHostSpec);

        DynamicCluster hosts = addChild(EntitySpec.create(DynamicCluster.class)
                .configure(Cluster.INITIAL_SIZE, initialSize)
                .configure(DynamicCluster.QUARANTINE_FAILED_ENTITIES, true)
                .configure(DynamicCluster.MEMBER_SPEC, dockerHostSpec)
                .configure(DynamicCluster.RUNNING_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty())
                .configure(DynamicCluster.UP_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty())
                .configure(BrooklynCampConstants.PLAN_ID, "docker-hosts")
                .displayName("Docker Hosts"));

        EntitySpec<?> etcdNodeSpec = EntitySpec.create(config().get(EtcdCluster.ETCD_NODE_SPEC));
        String etcdVersion = config().get(ETCD_VERSION);
        if (Strings.isNonBlank(etcdVersion)) {
            etcdNodeSpec.configure(SoftwareProcess.SUGGESTED_VERSION, etcdVersion);
        }
        sensors().set(EtcdCluster.ETCD_NODE_SPEC, etcdNodeSpec);

        EtcdCluster etcd = addChild(EntitySpec.create(EtcdCluster.class)
                .configure(Cluster.INITIAL_SIZE, 0)
                .configure(EtcdCluster.ETCD_NODE_SPEC, etcdNodeSpec)
                .configure(EtcdCluster.CLUSTER_NAME, "docker")
                .configure(EtcdCluster.CLUSTER_TOKEN, "etcd-docker")
                .configure(DynamicCluster.QUARANTINE_FAILED_ENTITIES, true)
                .configure(DynamicCluster.RUNNING_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty())
                .configure(DynamicCluster.UP_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty())
                .displayName("Etcd Cluster"));
        sensors().set(ETCD_CLUSTER, etcd);

        DynamicGroup fabric = addChild(EntitySpec.create(DynamicGroup.class)
                .configure(DynamicGroup.ENTITY_FILTER, Predicates.and(Predicates.instanceOf(DockerContainer.class), EntityPredicates.attributeEqualTo(DockerContainer.DOCKER_INFRASTRUCTURE, this)))
                .displayName("All Docker Containers"));

        if (config().get(SDN_ENABLE) && config().get(SDN_PROVIDER_SPEC) != null) {
            EntitySpec entitySpec = EntitySpec.create(config().get(SDN_PROVIDER_SPEC));
            entitySpec.configure(DockerAttributes.DOCKER_INFRASTRUCTURE, this);
            Entity sdn = addChild(entitySpec);
            sensors().set(SDN_PROVIDER, sdn);
        }

        sensors().set(DOCKER_HOST_CLUSTER, hosts);
        sensors().set(DOCKER_CONTAINER_FABRIC, fabric);

        hosts.enrichers().add(Enrichers.builder()
                .aggregating(MachineAttributes.CPU_USAGE)
                .computingAverage()
                .fromMembers()
                .publishing(MachineAttributes.AVERAGE_CPU_USAGE)
                .valueToReportIfNoSensors(0d)
                .build());
        hosts.enrichers().add(Enrichers.builder()
                .aggregating(DOCKER_CONTAINER_COUNT)
                .computingSum()
                .fromMembers()
                .publishing(DOCKER_CONTAINER_COUNT)
                .build());

        enrichers().add(Enrichers.builder()
                .propagating(DOCKER_CONTAINER_COUNT, MachineAttributes.AVERAGE_CPU_USAGE)
                .from(hosts)
                .build());
        enrichers().add(Enrichers.builder()
                .propagating(ImmutableMap.of(DynamicCluster.GROUP_SIZE, DOCKER_HOST_COUNT))
                .from(hosts)
                .build());

        Integer headroom = config().get(ContainerHeadroomEnricher.CONTAINER_HEADROOM);
        Double headroomPercent = config().get(ContainerHeadroomEnricher.CONTAINER_HEADROOM_PERCENTAGE);
        if ((headroom != null && headroom > 0) || (headroomPercent != null && headroomPercent > 0d)) {
            enrichers().add(EnricherSpec.create(ContainerHeadroomEnricher.class)
                    .configure(ContainerHeadroomEnricher.CONTAINER_HEADROOM, headroom)
                    .configure(ContainerHeadroomEnricher.CONTAINER_HEADROOM_PERCENTAGE, headroomPercent));
            hosts.enrichers().add(Enrichers.builder()
                    .propagating(
                            ContainerHeadroomEnricher.DOCKER_CONTAINER_CLUSTER_COLD,
                            ContainerHeadroomEnricher.DOCKER_CONTAINER_CLUSTER_HOT,
                            ContainerHeadroomEnricher.DOCKER_CONTAINER_CLUSTER_OK)
                    .from(this)
                    .build());
            hosts.policies().add(PolicySpec.create(AutoScalerPolicy.class)
                    .configure(AutoScalerPolicy.POOL_COLD_SENSOR, ContainerHeadroomEnricher.DOCKER_CONTAINER_CLUSTER_COLD)
                    .configure(AutoScalerPolicy.POOL_HOT_SENSOR, ContainerHeadroomEnricher.DOCKER_CONTAINER_CLUSTER_HOT)
                    .configure(AutoScalerPolicy.POOL_OK_SENSOR, ContainerHeadroomEnricher.DOCKER_CONTAINER_CLUSTER_OK)
                    .configure(AutoScalerPolicy.MIN_POOL_SIZE, initialSize)
                    .configure(AutoScalerPolicy.RESIZE_UP_STABILIZATION_DELAY, Duration.THIRTY_SECONDS)
                    .configure(AutoScalerPolicy.RESIZE_DOWN_STABILIZATION_DELAY, Duration.FIVE_MINUTES)
                    .displayName("Headroom Auto Scaler"));
        }

        sensors().set(Attributes.MAIN_URI, URI.create("/clocker"));

        // Override the health-check: just interested in the docker infrastructure/SDN, rather than 
        // the groups that show the apps.
        Entity sdn = sensors().get(SDN_PROVIDER);
        enrichers().add(EnricherSpec.create(ComputeServiceIndicatorsFromChildrenAndMembers.class)
                .uniqueTag(ComputeServiceIndicatorsFromChildrenAndMembers.DEFAULT_UNIQUE_TAG)
                .configure(ComputeServiceIndicatorsFromChildrenAndMembers.FROM_CHILDREN, true)
                .configure(ComputeServiceIndicatorsFromChildrenAndMembers.ENTITY_FILTER, Predicates.or(
                        Predicates.<Entity>equalTo(hosts), (sdn == null ? Predicates.<Entity>alwaysFalse() : Predicates.equalTo(sdn)))));
    }

    @Override
    public String getIconUrl() { return "classpath://docker-logo.png"; }

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
            LOG.debug("Resize Docker infrastructure to {} at {}", new Object[]{ desiredSize, getLocations() });
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

        DockerLocation location = getManagementContext().getLocationManager().createLocation(LocationSpec.create(DockerLocation.class)
                .configure(flags)
                .configure("owner", getProxy())
                .configure("locationName", locationName));
        
        LocationDefinition definition = location.register();

        sensors().set(LocationOwner.LOCATION_SPEC, definition.getSpec());
        sensors().set(LocationOwner.DYNAMIC_LOCATION, location);
        sensors().set(LocationOwner.LOCATION_NAME, locationName);

        LOG.info("New Docker location {} created for {}", location, this);
        return location;
    }

    @Override
    public void deleteLocation() {
        DockerLocation location = getDynamicLocation();

        if (location != null) {
            location.deregister();
            Locations.unmanage(location);
        }

        sensors().set(LocationOwner.DYNAMIC_LOCATION, null);
        sensors().set(LocationOwner.LOCATION_NAME, null);
    }

    @Override
    public void doStart(Collection<? extends Location> locs) {
        addLocations(locs);
        List<Location> locations = MutableList.copyOf(Locations.getLocationsCheckingAncestors(locs, this));

        sensors().set(SERVICE_UP, Boolean.FALSE);
        ServiceStateLogic.setExpectedState(this, Lifecycle.STARTING);

        // TODO support multiple provisioners
        Location provisioner = Iterables.getOnlyElement(locations);
        LOG.info("Creating new DockerLocation wrapping {}", provisioner);

        Map<String, ?> flags = MutableMap.<String, Object>builder()
                .putAll(config().get(LOCATION_FLAGS))
                .put("provisioner", provisioner)
                .putIfNotNull("strategies", config().get(PLACEMENT_STRATEGIES))
                .build();
        createLocation(flags);

        super.doStart(locations);

        ServiceStateLogic.setExpectedState(this, Lifecycle.RUNNING);
        sensors().set(SERVICE_UP, Boolean.TRUE);
    }

    @Override
    public void postStart(Collection<? extends Location> locations) {
        if (config().get(DOCKER_SHOULD_START_REGISTRY)) {
            DockerHost firstEntity = (DockerHost) sensors().get(DOCKER_HOST_CLUSTER).sensors().get(DynamicCluster.FIRST);

            EntitySpec<DockerRegistry> spec = EntitySpec.create(DockerRegistry.class)
                    .configure(DockerRegistry.DOCKER_HOST, firstEntity)
                    .configure(DockerRegistry.DOCKER_REGISTRY_PORT, config().get(DOCKER_REGISTRY_PORT));

            // TODO Mount volume with images stored on it
            DockerRegistry registry = addChild(spec);

            LOG.debug("Starting a new Docker Registry with spec {}", spec);
            Entities.start(registry, ImmutableList.of(firstEntity.getDynamicLocation()));

            String registryUrl = HostAndPort.fromParts(firstEntity.sensors().get(Attributes.ADDRESS), config().get(DOCKER_REGISTRY_PORT)).toString();
            sensors().set(DOCKER_IMAGE_REGISTRY_URL, registryUrl);
            sensors().set(DOCKER_IMAGE_REGISTRY, registry);
        }
    }

    /**
     * De-register our {@link DockerLocation} and its children.
     */
    @Override
    public void stop() {
        sensors().set(SERVICE_UP, Boolean.FALSE);
        ServiceStateLogic.setExpectedState(this, Lifecycle.STOPPING);
        Duration timeout = config().get(SHUTDOWN_TIMEOUT);

        deleteLocation();

        // Shutdown the Registry if configured
        if (config().get(DOCKER_SHOULD_START_REGISTRY)) {
            Entity registry = sensors().get(DOCKER_IMAGE_REGISTRY);
            DockerUtils.stop(this, registry, Duration.THIRTY_SECONDS);
        }

        // Find all applications and stop, blocking for up to five minutes until ended
        try {
            Iterable<Entity> entities = Iterables.filter(getManagementContext().getEntityManager().getEntities(),
                    Predicates.and(DockerUtils.sameInfrastructure(this), Predicates.not(EntityPredicates.applicationIdEqualTo(getApplicationId()))));
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

        // Stop all Docker hosts in parallel
        try {
            DynamicCluster hosts = getDockerHostCluster();
            LOG.debug("Stopping hosts: {}", Iterables.toString(hosts.getMembers()));
            Entities.invokeEffectorList(this, hosts.getMembers(), Startable.STOP).get(timeout);
        } catch (Exception e) {
            LOG.warn("Error stopping hosts", e);
        }

        ServiceStateLogic.setExpectedState(this, Lifecycle.STOPPED);
    }

    static {
        DockerAttributes.init();

        RendererHints.register(DOCKER_HOST_CLUSTER, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(DOCKER_CONTAINER_FABRIC, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(SDN_PROVIDER, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(ETCD_CLUSTER, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
    }

}
