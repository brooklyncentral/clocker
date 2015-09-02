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
package brooklyn.entity.container;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigInheritance;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.core.internal.ssh.SshTool;
import org.apache.brooklyn.util.javalang.Reflections;

import brooklyn.location.docker.strategy.DockerAwarePlacementStrategy;

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

    public static final AttributeSensorAndConfigKey<String, String> DOCKER_IMAGE_REPOSITORY = ConfigKeys.newStringSensorAndConfigKey(
            "docker.image.repository", "The repository of the Docker image used by a container");

    public static final AttributeSensorAndConfigKey<String, String> DOCKER_IMAGE_NAME = ConfigKeys.newStringSensorAndConfigKey(
            "docker.image.name", "The name of the Docker image used by a container");

    public static final AttributeSensorAndConfigKey<String, String> DOCKER_IMAGE_TAG = ConfigKeys.newStringSensorAndConfigKey(
            "docker.image.tag", "The tag of the image to use", "latest");

    public static final AttributeSensorAndConfigKey<String, String> DOCKER_HARDWARE_ID = ConfigKeys.newStringSensorAndConfigKey(
            "docker.hardwareId", "The ID of a Docker hardware type to use for a container", "small");

    public static final AttributeSensorAndConfigKey<String, String> DOCKER_CONTAINER_NAME = ConfigKeys.newStringSensorAndConfigKey(
            "docker.container.name", "The name of the Docker container");

    public static final ConfigKey<String> DOCKER_PASSWORD = ConfigKeys.newConfigKeyWithPrefix("docker.", SshTool.PROP_PASSWORD);

    public static final ConfigKey<Boolean> DOCKER_USE_HOST_DNS_NAME = ConfigKeys.newBooleanConfigKey(
            "docker.useHostDnsName", "Container uses same DNS hostname as Docker host", Boolean.TRUE);

    public static final ConfigKey<Boolean> DOCKER_USE_SSH = ConfigKeys.newBooleanConfigKey(
            "docker.useSsh", "Use SSH layer instead of docker exec for container commands", Boolean.TRUE);

    public static final ConfigKey<Integer> DOCKER_CPU_SHARES = ConfigKeys.newIntegerConfigKey(
            "docker.cpuShares", "Container CPU shares configuration");

    public static final ConfigKey<Integer> DOCKER_MEMORY = ConfigKeys.newIntegerConfigKey(
            "docker.memory", "Container memory configuration");

    public static final ConfigKey<Boolean> PRIVILEGED = ConfigKeys.newBooleanConfigKey(
            "docker.container.privileged", "Set to true if the container is to be provileged", Boolean.TRUE);

    public static final ConfigKey<Boolean> MANAGED = ConfigKeys.newBooleanConfigKey(
            "docker.container.managed", "Set to false if the container is not managed by Brooklyn and Clocker", Boolean.TRUE);

    public static final AttributeSensorAndConfigKey<Map<String, String>, Map<String, String>> DOCKER_HOST_VOLUME_MAPPING = ConfigKeys.newSensorAndConfigKey(
            new TypeToken<Map<String, String>>() { },
            "docker.host.volumes", "Host volume mapping configuration");

    public static final ConfigKey<List<String>> DOCKER_CONTAINER_VOLUME_EXPORT = ConfigKeys.newConfigKey(
            new TypeToken<List<String>>() { },
            "docker.container.volumes", "Container volume export configuration");

    public static final ConfigKey<List<DockerAwarePlacementStrategy>> PLACEMENT_STRATEGIES = ConfigKeys.newConfigKey(
            new TypeToken<List<DockerAwarePlacementStrategy>>() { },
            "docker.container.strategies", "Placement strategy list for Docker containers");

    public static final AttributeSensorAndConfigKey<Entity, Entity> DOCKER_INFRASTRUCTURE = ConfigKeys.newSensorAndConfigKey(Entity.class,
            "docker.infrastructure", "The Docker infrastructure");

    // Thes configurations must be set on the specific entity and will not be inherited

    public static final ConfigKey<List<PortAttributeSensorAndConfigKey>> DOCKER_DIRECT_PORT_CONFIG = ConfigKeys.builder(new TypeToken<List<PortAttributeSensorAndConfigKey>>() { })
            .name("docker.container.directPorts.configKeys")
            .description("List of configration keys for ports that are to be mapped directly on the Docker host")
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
            .description("List of extra ports to open on the container for forwarding")
            .defaultValue(ImmutableList.<Integer>of())
            .inheritance(ConfigInheritance.NONE)
            .build();

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
