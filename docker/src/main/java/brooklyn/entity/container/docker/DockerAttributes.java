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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.database.DatastoreMixins;
import brooklyn.entity.messaging.MessageBroker;
import brooklyn.entity.nosql.couchbase.CouchbaseNode;
import brooklyn.entity.webapp.WebAppServiceConstants;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.location.docker.strategy.DockerAwarePlacementStrategy;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.javalang.Reflections;

import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;

public class DockerAttributes {

    /** Do not instantiate. */
    private DockerAttributes() { }

    /*
     * Configuration and constants.
     */

    public static final Set<String> URL_SENSOR_NAMES = ImmutableSet.<String>of(
            WebAppServiceConstants.ROOT_URL.getName(),
            DatastoreMixins.DATASTORE_URL.getName(),
            CouchbaseNode.COUCHBASE_WEB_ADMIN_URL.getName(),
            MessageBroker.BROKER_URL.getName());

    public static final String DEFAULT_DOCKER_CONTAINER_NAME_FORMAT = "docker-container-brooklyn-%1$s";
    public static final String DEFAULT_DOCKER_HOST_NAME_FORMAT = "docker-host-brooklyn-%1$s";

    public static final String UBUNTU_DOCKERFILE = "classpath://brooklyn/entity/container/docker/ubuntu/Dockerfile";
    public static final String CENTOS_DOCKERFILE = "classpath://brooklyn/entity/container/docker/centos/Dockerfile";
    public static final String COREOS_DOCKERFILE = "classpath://brooklyn/entity/container/docker/coreos/Dockerfile";

    /** Valid characters for the Dockerfile location. */
    public static final CharMatcher DOCKERFILE_CHARACTERS = CharMatcher.anyOf("-_.")
            .or(CharMatcher.inRange('a', 'z'))
            .or(CharMatcher.inRange('0', '9'));
    public static final CharMatcher DOCKERFILE_INVALID_CHARACTERS = DOCKERFILE_CHARACTERS.negate();

    public static final ConfigKey<String> DOCKERFILE_URL = ConfigKeys.newStringConfigKey("docker.dockerfile.url", "URL of a DockerFile to use");

    public static final AttributeSensorAndConfigKey<String, String> DOCKER_IMAGE_ID = ConfigKeys.newSensorAndConfigKey(String.class, "docker.imageId", "The ID of a Docker image to use for a container");

    public static final AttributeSensorAndConfigKey<String, String> DOCKER_HARDWARE_ID = ConfigKeys.newSensorAndConfigKey(String.class, "docker.hardwareId", "The ID of a Docker hardware type to use for a container", "small");

    public static final ConfigKey<Boolean> DOCKER_USE_HOST_DNS_NAME = ConfigKeys.newBooleanConfigKey("docker.useHostDnsName", "Container uses same DNS hostname as Docker host", Boolean.TRUE);
    public static final ConfigKey<Integer> DOCKER_CPU_SHARES = ConfigKeys.newIntegerConfigKey("docker.cpuShares", "Container CPU shares configuration");
    public static final ConfigKey<Integer> DOCKER_MEMORY = ConfigKeys.newIntegerConfigKey("docker.memory", "Container memory configuration");

    public static final AttributeSensorAndConfigKey<Map<String, String>, Map<String, String>> DOCKER_HOST_VOLUME_MAPPING = ConfigKeys.newSensorAndConfigKey(
            new TypeToken<Map<String, String>>() { }, "docker.host.volumes", "Host volume mapping configuration");
    public static final ConfigKey<List<String>> DOCKER_CONTAINER_VOLUME_EXPORT = ConfigKeys.newConfigKey(
            new TypeToken<List<String>>() { }, "docker.container.volumes", "Container volume export configuration");

    /*
     * Counter attributes.
     */

    public static final AttributeSensor<Integer> DOCKER_HOST_COUNT = Sensors.newIntegerSensor("docker.hostCount", "Number of Docker hosts");
    public static final AttributeSensor<Integer> DOCKER_CONTAINER_COUNT = Sensors.newIntegerSensor("docker.containerCount", "Number of Docker containers");
    public static final AttributeSensor<Integer> DOCKER_IDLE_HOST_COUNT = Sensors.newIntegerSensor("docker.hostCount.idle", "Number of idle Docker hosts");
    public static final AttributeSensor<Integer> DOCKER_IDLE_CONTAINER_COUNT = Sensors.newIntegerSensor("docker.containerCount.idle", "Number of idle Docker containers");

    private static AtomicBoolean initialized = new AtomicBoolean(false);

    /** Setup renderer hints. */
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
