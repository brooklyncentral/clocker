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
package brooklyn.entity.container;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.location.docker.strategy.DockerAwarePlacementStrategy;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.javalang.Reflections;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.reflect.TypeToken;

public class DockerAttributes {

    /** Do not instantiate. */
    private DockerAttributes() { }

    /*
     * Configuration.
     */

    public static final ConfigKey<String> DOCKERFILE_URL = ConfigKeys.newStringConfigKey("docker.dockerfile.url", "URL of a Dockerfile to use");
    public static final ConfigKey<String> DOCKERFILE_NAME = ConfigKeys.newStringConfigKey("docker.dockerfile.name", "Name for the image created by the Dockerfile being used");

    public static final AttributeSensorAndConfigKey<String, String> DOCKER_IMAGE_ID = ConfigKeys.newStringSensorAndConfigKey("docker.imageId", "The ID of a Docker image to use for a container");

    public static final AttributeSensorAndConfigKey<String, String> DOCKER_IMAGE_NAME = ConfigKeys.newStringSensorAndConfigKey("docker.imageName", "The name of the Docker image use used by a container");

    public static final AttributeSensorAndConfigKey<String, String> DOCKER_HARDWARE_ID = ConfigKeys.newSensorAndConfigKey(String.class, "docker.hardwareId", "The ID of a Docker hardware type to use for a container", "small");

    public static final ConfigKey<String> DOCKER_PASSWORD = ConfigKeys.newConfigKeyWithPrefix("docker.", SshTool.PROP_PASSWORD);

    public static final ConfigKey<Boolean> DOCKER_USE_HOST_DNS_NAME = ConfigKeys.newBooleanConfigKey("docker.useHostDnsName", "Container uses same DNS hostname as Docker host", Boolean.TRUE);
    public static final ConfigKey<Integer> DOCKER_CPU_SHARES = ConfigKeys.newIntegerConfigKey("docker.cpuShares", "Container CPU shares configuration");
    public static final ConfigKey<Integer> DOCKER_MEMORY = ConfigKeys.newIntegerConfigKey("docker.memory", "Container memory configuration");

    public static final ConfigKey<Boolean> MANAGED = ConfigKeys.newBooleanConfigKey("docker.container.managed", "Set to false if the container is not managed by Brooklyn and Clocker", Boolean.TRUE);

    public static final AttributeSensorAndConfigKey<Map<String, String>, Map<String, String>> DOCKER_HOST_VOLUME_MAPPING = ConfigKeys.newSensorAndConfigKey(
            new TypeToken<Map<String, String>>() { }, "docker.host.volumes", "Host volume mapping configuration");
    public static final ConfigKey<List<String>> DOCKER_CONTAINER_VOLUME_EXPORT = ConfigKeys.newConfigKey(
            new TypeToken<List<String>>() { }, "docker.container.volumes", "Container volume export configuration");

    public static final ConfigKey<List<DockerAwarePlacementStrategy>> PLACEMENT_STRATEGIES = ConfigKeys.newConfigKey(
            new TypeToken<List<DockerAwarePlacementStrategy>>() { },
            "docker.container.strategies", "Placement strategy list for Docker containers");

    public static final AttributeSensorAndConfigKey<Entity, Entity> DOCKER_INFRASTRUCTURE = ConfigKeys.newSensorAndConfigKey(Entity.class,
            "docker.infrastructure", "The Docker infrastructure");

    public static final ConfigKey<Boolean> SDN_ENABLE = ConfigKeys.newBooleanConfigKey("sdn.enable", "Enable Sofware Defined Networking", Boolean.FALSE);

    /*
     * Counter attributes.
     */

    public static final AttributeSensor<Integer> DOCKER_HOST_COUNT = Sensors.newIntegerSensor("docker.hosts.total", "Number of Docker hosts");
    public static final AttributeSensor<Integer> DOCKER_CONTAINER_COUNT = Sensors.newIntegerSensor("docker.containers.total", "Number of Docker containers");
    public static final AttributeSensor<Integer> DOCKER_IDLE_HOST_COUNT = Sensors.newIntegerSensor("docker.hosts.idle", "Number of idle Docker hosts");
    public static final AttributeSensor<Integer> DOCKER_IDLE_CONTAINER_COUNT = Sensors.newIntegerSensor("docker.containers.idle", "Number of idle Docker containers");

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
