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

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.reflect.TypeToken;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.catalog.CatalogConfig;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.trait.Resizable;
import org.apache.brooklyn.core.location.dynamic.LocationOwner;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.entity.group.DynamicGroup;
import org.apache.brooklyn.entity.group.DynamicMultiGroup;
import org.apache.brooklyn.entity.stock.BasicStartable;
import org.apache.brooklyn.sensor.core.AttributeSensorAndConfigKey;
import org.apache.brooklyn.sensor.core.BasicAttributeSensorAndConfigKey;
import org.apache.brooklyn.sensor.core.Sensors;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.time.Duration;

import brooklyn.entity.container.DockerAttributes;
import brooklyn.entity.container.DockerUtils;
import brooklyn.location.docker.DockerLocation;
import brooklyn.location.docker.strategy.DepthFirstPlacementStrategy;
import brooklyn.location.docker.strategy.DockerAwarePlacementStrategy;
import brooklyn.location.docker.strategy.affinity.AffinityRules;
import brooklyn.networking.sdn.SdnAttributes;

/**
 * A collection of machines running Docker.
 */
@Catalog(name = "Docker Infrastructure",
        description = "Docker is an open-source engine to easily create lightweight, portable, self-sufficient containers from any application.",
        iconUrl = "classpath:///docker-logo.png")
@ImplementedBy(DockerInfrastructureImpl.class)
public interface DockerInfrastructure extends BasicStartable, Resizable, LocationOwner<DockerLocation, DockerInfrastructure> {

    @CatalogConfig(label = "Location Name", priority = 90)
    @SetFromFlag("locationName")
    BasicAttributeSensorAndConfigKey<String> LOCATION_NAME =LocationOwner.LOCATION_NAME;

    @CatalogConfig(label = "Docker Version", priority = 10)
    @SetFromFlag("dockerVersion")
    ConfigKey<String> DOCKER_VERSION = ConfigKeys.newStringConfigKey("docker.version", "The Docker Engine version number", "1.7.1");

    @SetFromFlag("securityGroup")
    ConfigKey<String> SECURITY_GROUP = ConfigKeys.newStringConfigKey(
            "docker.host.securityGroup", "Set a network security group for cloud servers to use; (null to use default configuration)");

    @CatalogConfig(label = "Docker Cluster Size", priority = 50)
    @SetFromFlag("minHost")
    ConfigKey<Integer> DOCKER_HOST_CLUSTER_MIN_SIZE = ConfigKeys.newConfigKeyWithPrefix("docker.host.", DynamicCluster.INITIAL_SIZE);

    @SetFromFlag("strategies")
    ConfigKey<List<DockerAwarePlacementStrategy>> PLACEMENT_STRATEGIES = ConfigKeys.newConfigKeyWithDefault(DockerAttributes.PLACEMENT_STRATEGIES,
            MutableList.<DockerAwarePlacementStrategy>of(new DepthFirstPlacementStrategy()));

    @SetFromFlag("highAvailabilty")
    ConfigKey<Boolean> HA_POLICY_ENABLE = ConfigKeys.newBooleanConfigKey("docker.policy.ha.enable",
            "Enable high-availability and resilience/restart policies", Boolean.FALSE);

    @SetFromFlag("removeEmptyHosts")
    ConfigKey<Boolean> REMOVE_EMPTY_DOCKER_HOSTS = ConfigKeys.newBooleanConfigKey("docker.host.removeEmpty",
            "Remove empty Docker Hosts with no containers", Boolean.FALSE);

    @SetFromFlag("enableSdn")
    ConfigKey<Boolean> SDN_ENABLE = SdnAttributes.SDN_ENABLE;

    @SetFromFlag("sdnProviderSpec")
    ConfigKey<EntitySpec> SDN_PROVIDER_SPEC = ConfigKeys.newConfigKey(EntitySpec.class, "sdn.provider.spec", "SDN provider entity specification");

    @SetFromFlag("hostSpec")
    AttributeSensorAndConfigKey<EntitySpec, EntitySpec> DOCKER_HOST_SPEC = ConfigKeys.newSensorAndConfigKey(
            EntitySpec.class, "docker.host.spec", "Specification to use when creating child Docker Hosts",
            EntitySpec.create(DockerHost.class));

    @SetFromFlag("generateCerts")
    ConfigKey<Boolean> DOCKER_GENERATE_TLS_CERTIFICATES = ConfigKeys.newBooleanConfigKey("docker.tls.generate", "Generate the TLS required TLS certificate and keys for each host", Boolean.TRUE);

    ConfigKey<String> DOCKER_CA_CERTIFICATE_PATH = ConfigKeys.newStringConfigKey("docker.tls.caCert", "The Docker Engine TLS CA certificate PEM file path", "conf/ca-cert.pem");
    ConfigKey<String> DOCKER_CA_KEY_PATH = ConfigKeys.newStringConfigKey("docker.tls.caKey", "The Docker Engine TLS CA certificate PEM file path", "conf/ca-key.pem");

    ConfigKey<String> DOCKER_SERVER_CERTIFICATE_PATH = ConfigKeys.newStringConfigKey("docker.tls.serverCert", "The Docker Engine TLS Server certificate PEM file path");
    ConfigKey<String> DOCKER_SERVER_KEY_PATH = ConfigKeys.newStringConfigKey("docker.tls.serverKey", "The Docker Engine TLS Server key PEM file path");

    ConfigKey<String> DOCKER_CLIENT_CERTIFICATE_PATH = ConfigKeys.newStringConfigKey("docker.tls.clientCert", "The Docker Engine TLS Client certificate PEM file path");
    ConfigKey<String> DOCKER_CLIENT_KEY_PATH = ConfigKeys.newStringConfigKey("docker.tls.clientKey", "The Docker Engine TLS Client key PEM file path");

    @SetFromFlag("dockerfileUrl")
    ConfigKey<String> DOCKERFILE_URL = ConfigKeys.newConfigKeyWithDefault(DockerAttributes.DOCKERFILE_URL, DockerUtils.UBUNTU_DOCKERFILE);

    @SetFromFlag("dockerfileName")
    ConfigKey<String> DOCKERFILE_NAME = ConfigKeys.newConfigKeyWithDefault(DockerAttributes.DOCKERFILE_NAME, "ubuntu");

    @SetFromFlag("imageId")
    ConfigKey<String> DOCKER_IMAGE_ID = DockerAttributes.DOCKER_IMAGE_ID.getConfigKey();

    @SetFromFlag("hardwareId")
    ConfigKey<String> DOCKER_HARDWARE_ID = DockerAttributes.DOCKER_HARDWARE_ID.getConfigKey();

    @SetFromFlag("affinityRules")
    ConfigKey<List<String>> DOCKER_HOST_AFFINITY_RULES = AffinityRules.AFFINITY_RULES;

    @SetFromFlag("shutdownTimeout")
    ConfigKey<Duration> SHUTDOWN_TIMEOUT = ConfigKeys.newDurationConfigKey("docker.timeout.shutdown", "Timeout to wait for children when shutting down", Duration.FIVE_MINUTES);

    @SetFromFlag("substitutions")
    ConfigKey<Map<String, Object>> DOCKERFILE_SUBSTITUTIONS = ConfigKeys.newConfigKey(
            new TypeToken<Map<String, Object>>() { },
            "docker.dockerfile.substitutions", "Dockerfile template substitutions", MutableMap.<String, Object>of());

    AttributeSensor<DynamicCluster> DOCKER_HOST_CLUSTER = Sensors.newSensor(DynamicCluster.class, "docker.hosts", "Docker host cluster");
    AttributeSensor<DynamicGroup> DOCKER_CONTAINER_FABRIC = Sensors.newSensor(DynamicGroup.class, "docker.fabric", "Docker container fabric");
    AttributeSensor<DynamicMultiGroup> DOCKER_APPLICATIONS = Sensors.newSensor(DynamicMultiGroup.class, "docker.buckets", "Docker applications");
    AttributeSensor<Entity> SDN_PROVIDER = Sensors.newSensor(Entity.class, "sdn.provider.network", "SDN provider network entity");

    AttributeSensor<AtomicInteger> DOCKER_HOST_COUNTER = Sensors.newSensor(AtomicInteger.class, "docker.hosts.counter", "Docker host counter");
    AttributeSensor<AtomicInteger> DOCKER_CONTAINER_COUNTER = Sensors.newSensor(AtomicInteger.class, "docker.containers.counter", "Docker container counter");;

    AttributeSensor<Integer> DOCKER_HOST_COUNT = DockerAttributes.DOCKER_HOST_COUNT;
    AttributeSensor<Integer> DOCKER_CONTAINER_COUNT = DockerAttributes.DOCKER_CONTAINER_COUNT;

    List<Entity> getDockerHostList();

    DynamicCluster getDockerHostCluster();

    List<Entity> getDockerContainerList();

    DynamicGroup getContainerFabric();
}
