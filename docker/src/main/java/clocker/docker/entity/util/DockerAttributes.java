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
package clocker.docker.entity.util;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import clocker.docker.location.strategy.DockerAwarePlacementStrategy;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigInheritance;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.location.jclouds.JcloudsLocationConfig;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.javalang.Reflections;

public class DockerAttributes {

    /** Do not instantiate. */
    private DockerAttributes() { }

    /*
     * Configuration.
     */

    public static final ConfigKey<String> DOCKERFILE_URL = ConfigKeys.newStringConfigKey(
            "docker.dockerfile.url", "URL of a Dockerfile to use");

    public static final ConfigKey<String> DOCKERFILE_ENTRYPOINT_URL = ConfigKeys.newStringConfigKey(
            "docker.entrypoint.url", "URL of the Dockerfile entrypoint script to use. (default is no script)");

    public static final ConfigKey<String> DOCKERFILE_CONTEXT_URL = ConfigKeys.newStringConfigKey(
            "docker.context.url", "URL of the Dockerfile context archive to use. (default is no context)");

    public static final ConfigKey<String> DOCKERFILE_NAME = ConfigKeys.newStringConfigKey(
            "docker.dockerfile.name", "Name for the image created by the Dockerfile being used");

    public static final AttributeSensorAndConfigKey<String, String> DOCKER_IMAGE_ID = ConfigKeys.newStringSensorAndConfigKey(
            "docker.image.id", "The ID of a Docker image to use for a container");

    public static final AttributeSensorAndConfigKey<String, String> DOCKER_IMAGE_REGISTRY_URL = ConfigKeys.newStringSensorAndConfigKey(
            "docker.registry.url", "The URL of the Docker image registry");

    public static final AttributeSensor<Entity> DOCKER_IMAGE_REGISTRY = Sensors.newSensor(Entity.class,
            "docker.registry.entity", "The Docker image registry entity");

    public static final AttributeSensorAndConfigKey<String, String> DOCKER_IMAGE_NAME = ConfigKeys.newStringSensorAndConfigKey(
            "docker.image.name", "The name of the Docker image used by a container");

    public static final AttributeSensorAndConfigKey<String, String> DOCKER_IMAGE_TAG = ConfigKeys.newStringSensorAndConfigKey(
            "docker.image.tag", "The tag of the image to use", "latest");

    public static final AttributeSensorAndConfigKey<List<String>, List<String>> DOCKER_IMAGE_ENTRYPOINT = ConfigKeys.newSensorAndConfigKey(
            new TypeToken<List<String>>() { },
            "docker.image.entrypoint", "Optional replacement for the image entrypoint command and arguments");

    public static final AttributeSensorAndConfigKey<List<String>, List<String>> DOCKER_IMAGE_COMMANDS = ConfigKeys.newSensorAndConfigKey(
            new TypeToken<List<String>>() { },
            "docker.image.commands", "Optional replacement for the image commands");

    public static final AttributeSensorAndConfigKey<String, String> DOCKER_HARDWARE_ID = ConfigKeys.newStringSensorAndConfigKey(
            "docker.hardwareId", "The ID of a Docker hardware type to use for a container", "small");

    public static final AttributeSensorAndConfigKey<String, String> DOCKER_CONTAINER_NAME = ConfigKeys.newStringSensorAndConfigKey(
            "docker.container.name", "The name of the Docker container");

    public static final ConfigKey<String> DOCKER_LOGIN_USER = ConfigKeys.newConfigKeyWithDefault(
            ConfigKeys.newConfigKeyWithPrefix("docker.", JcloudsLocationConfig.LOGIN_USER), "root");

    public static final ConfigKey<String> DOCKER_LOGIN_PASSWORD = ConfigKeys.newConfigKeyWithPrefix("docker.", JcloudsLocationConfig.LOGIN_USER_PASSWORD);

    public static final ConfigKey<Boolean> DOCKER_USE_HOST_DNS_NAME = ConfigKeys.newBooleanConfigKey(
            "docker.useHostDnsName", "Container uses same DNS hostname as Docker host");

    public static final ConfigKey<Boolean> DOCKER_USE_SSH = ConfigKeys.newBooleanConfigKey(
            "docker.useSsh", "Use SSH layer instead of docker exec for container commands", Boolean.TRUE);

    public static final AttributeSensor<String> DOCKER_MAPPED_SSH_PORT = Sensors.newStringSensor(
            "mapped.docker.port.22", "Docker mapping for SSH default port");

    public static final ConfigKey<Integer> DOCKER_CPU_SHARES = ConfigKeys.newIntegerConfigKey(
            "docker.cpuShares", "Container CPU shares configuration");

    public static final ConfigKey<Integer> DOCKER_MEMORY = ConfigKeys.newIntegerConfigKey(
            "docker.memory", "Container memory configuration");

    public static final ConfigKey<Boolean> PRIVILEGED = ConfigKeys.newBooleanConfigKey(
            "docker.container.privileged", "Set to true if the container is to be privileged", Boolean.TRUE);

    public static final ConfigKey<Boolean> INTERACTIVE = ConfigKeys.newBooleanConfigKey(
            "docker.container.interactive", "Set to true if STDIN is to be kept open for an interactive container", Boolean.FALSE);

    public static final ConfigKey<Boolean> MANAGED = ConfigKeys.newBooleanConfigKey(
            "docker.container.managed", "Set to false if the container is not managed by Brooklyn and Clocker", Boolean.TRUE);

    public static final AttributeSensorAndConfigKey<Map<String, String>, Map<String, String>> DOCKER_HOST_VOLUME_MAPPING = ConfigKeys.newSensorAndConfigKey(
            new TypeToken<Map<String, String>>() { },
            "docker.host.volumes", "Host volume mapping configuration");

    public static final ConfigKey<List<String>> DOCKER_CONTAINER_VOLUME_EXPORT = ConfigKeys.newConfigKey(
            new TypeToken<List<String>>() { },
            "docker.container.volumes.export", "Container volume export configuration");

    public static final ConfigKey<List<String>> DOCKER_CONTAINER_VOLUMES_FROM = ConfigKeys.builder(new TypeToken<List<String>>() { })
            .name("docker.container.volumes.import")
            .description("Container volume import configuration")
            .defaultValue(ImmutableList.<String>of())
            .inheritance(ConfigInheritance.NONE)
            .build();

    public static final AttributeSensor<Map<String, String>> DOCKER_VOLUME_MAPPING = Sensors.newSensor(
            new TypeToken<Map<String, String>>() { },
            "docker.container.volumes", "Container volume mappings");

    public static final ConfigKey<List<DockerAwarePlacementStrategy>> PLACEMENT_STRATEGIES = ConfigKeys.newConfigKey(
            new TypeToken<List<DockerAwarePlacementStrategy>>() { },
            "docker.container.strategies", "Placement strategy list for Docker containers");

    public static final AttributeSensorAndConfigKey<Entity, Entity> DOCKER_INFRASTRUCTURE = ConfigKeys.newSensorAndConfigKey(Entity.class,
            "docker.infrastructure", "The Docker infrastructure");

    // These configurations must be set on the specific entity; all but the first will not be
    // inherited.

    public static final ConfigKey<Boolean> AUTO_CHECKPOINT_DOCKER_IMAGE_POST_INSTALL = ConfigKeys.newBooleanConfigKey(
            "docker.container.autoCheckpointImage",
            "Whether to automatically create an image after the entity's install(), and subsequently re-use that image for the entity type",
            false);

    public static final ConfigKey<Map<String, Entity>> DOCKER_LINKS = ConfigKeys.builder(new TypeToken<Map<String, Entity>>() { })
            .name("docker.container.links")
            .description("List of linked entities for a container")
            .defaultValue(ImmutableMap.<String, Entity>of())
            .inheritance(ConfigInheritance.NONE)
            .build();

    public static final ConfigKey<List<PortAttributeSensorAndConfigKey>> DOCKER_DIRECT_PORT_CONFIG = ConfigKeys.builder(new TypeToken<List<PortAttributeSensorAndConfigKey>>() { })
            .name("docker.container.directPorts.configKeys")
            .description("List of configration keys for ports that are to be mapped directly on the Docker host")
            .defaultValue(ImmutableList.<PortAttributeSensorAndConfigKey>of())
            .inheritance(ConfigInheritance.NONE)
            .build();

    public static final ConfigKey<List<Integer>> DOCKER_DIRECT_PORTS = ConfigKeys.builder(new TypeToken<List<Integer>>() { })
            .name("docker.container.directPorts")
            .description( "List of ports that are to be mapped directly on the Docker host")
            .defaultValue(ImmutableList.<Integer>of())
            .inheritance(ConfigInheritance.NONE)
            .build();

    public static final ConfigKey<List<Integer>> DOCKER_OPEN_PORTS = ConfigKeys.builder(new TypeToken<List<Integer>>() { })
            .name("docker.container.openPorts")
            .description("List of ports to open on the container for forwarding")
            .defaultValue(ImmutableList.<Integer>of())
            .inheritance(ConfigInheritance.NONE)
            .build();

    public static final ConfigKey<List<PortAttributeSensorAndConfigKey>> DOCKER_OPEN_PORT_CONFIG = ConfigKeys.builder(new TypeToken<List<PortAttributeSensorAndConfigKey>>() { })
            .name("docker.container.openPorts.configKeys")
            .description("List of configration keys for ports to open on the container for forwarding")
            .defaultValue(ImmutableList.<PortAttributeSensorAndConfigKey>of())
            .inheritance(ConfigInheritance.NONE)
            .build();

    public static final ConfigKey<Map<Integer, Integer>> DOCKER_PORT_BINDINGS = ConfigKeys.builder(new TypeToken<Map<Integer, Integer>>() { })
            .name("docker.container.portBindings")
            .description("Map of port bindings from the host to the container")
            .defaultValue(ImmutableMap.<Integer, Integer>of())
            .inheritance(ConfigInheritance.NONE)
            .build();

    public static final AttributeSensor<Map<Integer, Integer>> DOCKER_CONTAINER_PORT_BINDINGS = Sensors.newSensor(new TypeToken<Map<Integer, Integer>>() { },
            "docker.container.portBindings", "Map of port bindings from the host to the container");

    public static final AttributeSensor<List<Integer>> DOCKER_CONTAINER_OPEN_PORTS = Sensors.newSensor(new TypeToken<List<Integer>>() { },
            "docker.container.openPorts", "List of ports to open on the container for forwarding");

    /*
     * Counter attributes.
     */
    public static final AttributeSensor<Integer> DOCKER_HOST_COUNT = Sensors.newIntegerSensor(
            "docker.hosts.total", "Number of Docker hosts");

    public static final AttributeSensor<Integer> DOCKER_CONTAINER_COUNT = Sensors.newIntegerSensor(
            "docker.containers.total", "Number of Docker containers");

    public static final AttributeSensor<Integer> DOCKER_IDLE_HOST_COUNT = Sensors.newIntegerSensor(
            "docker.hosts.idle", "Number of idle Docker hosts");

    public static final AttributeSensor<Integer> DOCKER_IDLE_CONTAINER_COUNT = Sensors.newIntegerSensor(
            "docker.containers.idle", "Number of idle Docker containers");

    private static AtomicBoolean initialized = new AtomicBoolean(false);

    /** Setup custom type coercions. */
    @SuppressWarnings("rawtypes")
    public static void init() {
        if (initialized.getAndSet(true)) return;

        TypeCoercions.registerAdapter(String.class, DockerAwarePlacementStrategy.class, new Function<String, DockerAwarePlacementStrategy>() {
            @Override
            public DockerAwarePlacementStrategy apply(final String input) {
                ClassLoader classLoader = DockerAwarePlacementStrategy.class.getClassLoader();
                Optional<DockerAwarePlacementStrategy> strategy = Reflections.<DockerAwarePlacementStrategy>invokeConstructorWithArgs(classLoader, input);
                if (strategy.isPresent()) {
                    return strategy.get();
                } else {
                    throw new IllegalStateException("Failed to create DockerAwarePlacementStrategy "+input);
                }
            }
        });
    }

    static {
        init();
    }
}
