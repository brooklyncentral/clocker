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

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import clocker.docker.entity.util.DockerAttributes;
import clocker.docker.entity.util.DockerUtils;
import clocker.docker.location.DockerLocation;
import clocker.docker.location.strategy.DockerAwarePlacementStrategy;
import clocker.docker.location.strategy.affinity.AffinityRules;
import clocker.docker.location.strategy.basic.DepthFirstPlacementStrategy;
import clocker.docker.networking.entity.sdn.util.SdnAttributes;

import com.google.common.reflect.TypeToken;

import org.apache.brooklyn.api.catalog.CatalogConfig;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.StartableApplication;
import org.apache.brooklyn.core.entity.trait.Resizable;
import org.apache.brooklyn.core.location.dynamic.LocationOwner;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.BasicAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.entity.group.DynamicGroup;
import org.apache.brooklyn.entity.group.DynamicMultiGroup;
import org.apache.brooklyn.entity.nosql.etcd.EtcdCluster;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.time.Duration;

/**
 * A collection of machines running Docker.
 */
@ImplementedBy(DockerInfrastructureImpl.class)
public interface DockerInfrastructure extends StartableApplication, Resizable, LocationOwner<DockerLocation, DockerInfrastructure> {

    @CatalogConfig(label = "Location Name", priority = 90)
    @SetFromFlag("locationName")
    BasicAttributeSensorAndConfigKey<String> LOCATION_NAME = LocationOwner.LOCATION_NAME;

    @CatalogConfig(label = "Docker Version", priority = 10)
    @SetFromFlag("dockerVersion")
    ConfigKey<String> DOCKER_VERSION = ConfigKeys.newStringConfigKey("docker.version", "The Docker Engine version number", "1.10.3");

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
    ConfigKey<EntitySpec> SDN_PROVIDER_SPEC = SdnAttributes.SDN_PROVIDER_SPEC;

    @SetFromFlag("hostSpec")
    AttributeSensorAndConfigKey<EntitySpec, EntitySpec> DOCKER_HOST_SPEC = ConfigKeys.newSensorAndConfigKey(
            EntitySpec.class, "docker.host.spec", "Specification to use when creating child Docker Hosts",
            EntitySpec.create(DockerHost.class));

    @SetFromFlag("generateCerts")
    ConfigKey<Boolean> DOCKER_GENERATE_TLS_CERTIFICATES = ConfigKeys.newBooleanConfigKey("docker.tls.generate", "Generate the TLS required TLS certificate and keys for each host", Boolean.TRUE);

    ConfigKey<String> DOCKER_CA_CERTIFICATE_PATH = ConfigKeys.newStringConfigKey("docker.tls.caCert", "The Docker Engine TLS CA certificate PEM file path", "ca-cert.pem");
    ConfigKey<String> DOCKER_CA_KEY_PATH = ConfigKeys.newStringConfigKey("docker.tls.caKey", "The Docker Engine TLS CA certificate PEM file path", "ca-key.pem");

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

    @CatalogConfig(label = "Start Registry", priority = 50)
    @SetFromFlag("registryStart")
    ConfigKey<Boolean> DOCKER_SHOULD_START_REGISTRY = ConfigKeys.newBooleanConfigKey("docker.registry.start", "Setup a docker registry and use it for pulls", Boolean.FALSE);

    @SetFromFlag("registryPort")
    ConfigKey<Integer> DOCKER_REGISTRY_PORT = ConfigKeys.newIntegerConfigKey("docker.registry.port", "", 5000);

    @SetFromFlag("registryWriteable")
    ConfigKey<Boolean> DOCKER_IMAGE_REGISTRY_WRITEABLE = ConfigKeys.newBooleanConfigKey("docker.registry.writeable", "Use the configured docker registry for pushes", Boolean.FALSE);

    @SetFromFlag("registryUrl")
    AttributeSensorAndConfigKey<String, String> DOCKER_IMAGE_REGISTRY_URL = DockerAttributes.DOCKER_IMAGE_REGISTRY_URL;

    @SetFromFlag("registryUsername")
    ConfigKey<String> DOCKER_IMAGE_REGISTRY_USERNAME = ConfigKeys.newStringConfigKey("docker.registry.username", "Username for docker registry access");

    @SetFromFlag("registryPassword")
    ConfigKey<String> DOCKER_IMAGE_REGISTRY_PASSWORD = ConfigKeys.newStringConfigKey("docker.registry.password", "Password for docker registry access");

    AttributeSensor<Entity> DOCKER_IMAGE_REGISTRY = DockerAttributes.DOCKER_IMAGE_REGISTRY;

    ConfigKey<Boolean> USE_JCLOUDS_HOSTNAME_CUSTOMIZER = ConfigKeys.newBooleanConfigKey("docker.hostname.customizer", "Fix issues with hostname in some clouds", Boolean.FALSE);

    @SetFromFlag("etcdVersion")
    ConfigKey<String> ETCD_VERSION = ConfigKeys.newStringConfigKey("etcd.version", "The Etcd version number", "2.3.1");

    ConfigKey<Boolean> EXTERNAL_ETCD_CLUSTER = ConfigKeys.newBooleanConfigKey("etcd.external", "Whether to use an external Etcd cluster", Boolean.FALSE);
    ConfigKey<Integer> EXTERNAL_ETCD_INITIAL_SIZE = ConfigKeys.newIntegerConfigKey("etcd.external.initialSize", "The initial size of the external Etcd cluster");
    AttributeSensorAndConfigKey<String, String> EXTERNAL_ETCD_URL = ConfigKeys.newStringSensorAndConfigKey("etcd.external.url", "The URL for the external Etcd cluster (if configured, no cluster will be provisioned)");

    AttributeSensor<EtcdCluster> ETCD_CLUSTER = Sensors.newSensor(EtcdCluster.class, "etcd.cluster", "The EtcdCluster entity for storing state");

    AttributeSensor<DynamicCluster> DOCKER_HOST_CLUSTER = Sensors.newSensor(DynamicCluster.class, "docker.hosts", "Docker host cluster");
    AttributeSensor<DynamicGroup> DOCKER_CONTAINER_FABRIC = Sensors.newSensor(DynamicGroup.class, "docker.fabric", "Docker container fabric");

    AttributeSensor<Entity> SDN_PROVIDER = SdnAttributes.SDN_PROVIDER;

    AttributeSensor<AtomicInteger> DOCKER_HOST_COUNTER = Sensors.newSensor(AtomicInteger.class, "docker.hosts.counter", "Docker host counter");
    AttributeSensor<AtomicInteger> DOCKER_CONTAINER_COUNTER = Sensors.newSensor(AtomicInteger.class, "docker.containers.counter", "Docker container counter");;

    AttributeSensor<Integer> DOCKER_HOST_COUNT = DockerAttributes.DOCKER_HOST_COUNT;
    AttributeSensor<Integer> DOCKER_CONTAINER_COUNT = DockerAttributes.DOCKER_CONTAINER_COUNT;

    List<Entity> getDockerHostList();

    DynamicCluster getDockerHostCluster();

    List<Entity> getDockerContainerList();

    DynamicGroup getContainerFabric();

    Object getInfrastructureMutex();

}
