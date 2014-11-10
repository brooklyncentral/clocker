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
import java.util.Locale;
import java.util.Set;

import javax.annotation.Nullable;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.database.DatastoreMixins;
import brooklyn.entity.messaging.MessageBroker;
import brooklyn.entity.nosql.couchbase.CouchbaseNode;
import brooklyn.entity.webapp.WebAppServiceConstants;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.Strings;

import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;

public class DockerUtils {

    /** Do not instantiate. */
    private DockerUtils() { }

    /*
     * Configuration and constants.
     */

    public static final String DOCKERFILE = "Dockerfile";

    public static final String MAPPED = "mapped";
    public static final String ENDPOINT = "endpoint";
    public static final String PORT = "port";

    public static final Set<String> URL_SENSOR_NAMES = ImmutableSet.<String>of(
            WebAppServiceConstants.ROOT_URL.getName(),
            DatastoreMixins.DATASTORE_URL.getName(),
            CouchbaseNode.COUCHBASE_WEB_ADMIN_URL.getName(),
            MessageBroker.BROKER_URL.getName());

    public static final String DEFAULT_DOCKER_CONTAINER_NAME_FORMAT = "docker-container-brooklyn-%1$s";
    public static final String DEFAULT_DOCKER_HOST_NAME_FORMAT = "docker-host-brooklyn-%1$s";

    public static final String UBUNTU_DOCKERFILE = "classpath://brooklyn/entity/container/docker/ubuntu/Dockerfile";
    public static final String UBUNTU_USES_JAVA_DOCKERFILE = "classpath://brooklyn/entity/container/docker/ubuntu/UsesJavaDockerfile";

    public static final String CENTOS_DOCKERFILE = "classpath://brooklyn/entity/container/docker/centos/Dockerfile";
    public static final String COREOS_DOCKERFILE = "classpath://brooklyn/entity/container/docker/coreos/Dockerfile";

    public static final String SSHD_DOCKERFILE = "classpath://brooklyn/entity/container/docker/SshdDockerfile";

    /** Valid characters for the Dockerfile location. */
    public static final CharMatcher DOCKERFILE_CHARACTERS = CharMatcher.anyOf("_")
            .or(CharMatcher.inRange('a', 'z'))
            .or(CharMatcher.inRange('0', '9'));

    /** Invalid characters for the Dockerfile location. */
    public static final CharMatcher DOCKERFILE_INVALID_CHARACTERS = DOCKERFILE_CHARACTERS.negate();

    public static <T> AttributeSensor<T> mappedSensor(AttributeSensor<?> source) {
        return (AttributeSensor<T>) Sensors.newSensorWithPrefix(MAPPED + ".", source);
    }
    public static AttributeSensor<String> mappedPortSensor(PortAttributeSensorAndConfigKey source) {
        return Sensors.newStringSensor(MAPPED + "." + source.getName(), source.getDescription() + " (Docker mapping)");
    }
    public static AttributeSensor<String> endpointSensor(PortAttributeSensorAndConfigKey source) {
        List<String> name = Lists.transform(source.getNameParts(), new Function<String, String>() {
            @Override
            public String apply(@Nullable String input) {
                String target = PORT;
                if (input.equals(target)) return ENDPOINT;
                if (input.endsWith(target)) {
                    return input.replace(target, ENDPOINT);
                }
                target = Strings.toInitialCapOnly(PORT);
                if (input.endsWith(target)) {
                    return input.replace(target, Strings.toInitialCapOnly(ENDPOINT));
                }
                return input;
            }
        });
        if (!name.contains(ENDPOINT)) name.add(ENDPOINT);
        return Sensors.newStringSensor(Joiner.on(".").join(name), source.getDescription() + " (Docker mapping)");
    }

    /**
     * Transforms the input to contain only valid characters.
     *
     * @see #ALLOWED
     * @see #DOCKERFILE_CHARACTERS
     */
    public static String allowed(String input) {
        return ALLOWED.apply(input);
    }

    public static final Function<String, String> ALLOWED = new Function<String, String>() {
        @Override
        public String apply(@Nullable String input) {
            if (input == null) return null;
            return DOCKERFILE_INVALID_CHARACTERS.collapseFrom(input.toLowerCase(Locale.ENGLISH), '_');
        }
    };

    /** Parse and return the ID returned from a Docker command. */
    public static String checkId(String input) {
        String imageId = Strings.trim(input).toLowerCase(Locale.ENGLISH);

        if (imageId.length() == 64 && DOCKERFILE_CHARACTERS.matchesAllOf(imageId)) {
            return imageId;
        } else {
            throw new IllegalStateException("Invalid image ID returned: " + imageId);
        }
    }

    public static String imageName(Entity entity, String dockerfile, String repository) {
        String simpleName = entity.getEntityType().getSimpleName();
        String version = entity.getConfig(SoftwareProcess.SUGGESTED_VERSION);

        String label = Joiner.on(":").skipNulls().join(simpleName, version, dockerfile, repository);
        return Identifiers.makeIdFromHash(Hashing.md5().hashString(label, Charsets.UTF_8).asLong()).toLowerCase(Locale.ENGLISH);
    }
}
