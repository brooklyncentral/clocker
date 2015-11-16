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

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationDefinition;
import org.apache.brooklyn.api.location.PortRange;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.core.config.render.RendererHints.Hint;
import org.apache.brooklyn.core.config.render.RendererHints.NamedActionWithUrl;
import org.apache.brooklyn.core.entity.EntityAndAttribute;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.database.DatastoreMixins;
import org.apache.brooklyn.entity.messaging.MessageBroker;
import org.apache.brooklyn.entity.nosql.couchbase.CouchbaseCluster;
import org.apache.brooklyn.entity.nosql.couchbase.CouchbaseNode;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.webapp.WebAppServiceConstants;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.text.Strings;

import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.location.docker.DockerContainerLocation;
import brooklyn.networking.sdn.SdnAttributes;
import brooklyn.networking.subnet.SubnetTier;

public class DockerUtils {

    private static final Logger LOG = LoggerFactory.getLogger(DockerUtils.class);

    /** Do not instantiate. */
    private DockerUtils() { }

    /*
     * Configuration and constants.
     */

    public static final String DOCKERFILE = "Dockerfile";
    public static final String ENTRYPOINT = "entrypoint.sh";

    public static final String MAPPED = "mapped";
    public static final String ENDPOINT = "endpoint";
    public static final String PORT = "port";

    public static final Set<String> URL_SENSOR_NAMES = ImmutableSet.<String>of(
            WebAppServiceConstants.ROOT_URL.getName(),
            DatastoreMixins.DATASTORE_URL.getName(),
            CouchbaseNode.COUCHBASE_WEB_ADMIN_URL.getName(),
            MessageBroker.BROKER_URL.getName());

    public static final Set<String> BLACKLIST_URL_SENSOR_NAMES = ImmutableSet.<String>of(
            SoftwareProcess.DOWNLOAD_URL.getName(),
            CouchbaseCluster.COUCHBASE_CLUSTER_CONNECTION_URL.getName());

    public static final String DEFAULT_DOCKER_CONTAINER_NAME_FORMAT = "docker-container-brooklyn-%1$s";
    public static final String DEFAULT_DOCKER_HOST_NAME_FORMAT = "docker-host-brooklyn-%1$s";

    public static final String UBUNTU_DOCKERFILE = "classpath://brooklyn/entity/container/docker/ubuntu/Dockerfile";
    public static final String UBUNTU_NETWORKING_DOCKERFILE = "classpath://brooklyn/entity/container/docker/ubuntu/NetworkingDockerfile";
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
    public static AttributeSensor<String> mappedPortSensor(AttributeSensor source) {
        return Sensors.newStringSensor(MAPPED + "." + source.getName(), source.getDescription() + " (Docker mapping)");
    }
    public static AttributeSensor<String> endpointSensor(AttributeSensor source) {
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

    public static void configureEnrichers(SubnetTier subnetTier, Entity entity) {
        for (AttributeSensor sensor : Iterables.filter(entity.getEntityType().getSensors(), AttributeSensor.class)) {
            if ((DockerUtils.URL_SENSOR_NAMES.contains(sensor.getName()) ||
                        sensor.getName().endsWith(".url") ||
                        URI.class.isAssignableFrom(sensor.getType())) &&
                    !DockerUtils.BLACKLIST_URL_SENSOR_NAMES.contains(sensor.getName())) {
                AttributeSensor<String> target = DockerUtils.<String>mappedSensor(sensor);
                entity.addEnricher(subnetTier.uriTransformingEnricher(
                        EntityAndAttribute.create(entity, sensor), target));
                Set<Hint<?>> hints = RendererHints.getHintsFor(sensor);
                for (Hint<?> hint : hints) {
                    RendererHints.register(target, (NamedActionWithUrl) hint);
                }
                LOG.debug("Mapped URL sensor: origin={}, mapped={}", sensor.getName(), target.getName());
            } else if (sensor.getName().matches("docker\\.port\\.[0-9]+") ||
                    PortAttributeSensorAndConfigKey.class.isAssignableFrom(sensor.getClass())) {
                AttributeSensor<String> target = DockerUtils.mappedPortSensor(sensor);
                entity.addEnricher(subnetTier.hostAndPortTransformingEnricher(
                        EntityAndAttribute.create(entity, sensor), target));
                LOG.debug("Mapped port sensor: origin={}, mapped={}", sensor.getName(), target.getName());
            }
        }
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

    public static String imageName(Entity entity, String dockerfile) {
        String simpleName = entity.getEntityType().getSimpleName();
        String version = entity.config().get(SoftwareProcess.SUGGESTED_VERSION);

        String label = Joiner.on(":").skipNulls().join(simpleName, version, dockerfile);
        return Identifiers.makeIdFromHash(Hashing.md5().hashString(label, Charsets.UTF_8).asLong()).toLowerCase(Locale.ENGLISH);
    }

    public static Map<Integer, Integer> getMappedPorts(Entity entity) {
        Map<Integer, Integer> ports = MutableMap.of();
        for (ConfigKey<?> k: entity.getEntityType().getConfigKeys()) {
            if (k instanceof PortAttributeSensorAndConfigKey) {
                String name = ((PortAttributeSensorAndConfigKey) k).getName();
                Integer p = ((PortAttributeSensorAndConfigKey) k).getAsSensorValue(entity);
                Integer m = entity.sensors().get(Sensors.newIntegerSensor("mapped." + name));
                if (m == null) {
                    ports.put(p, p);
                } else {
                    ports.put(m, p);
                }
            } else if (PortRange.class.isAssignableFrom(k.getType())) {
                PortRange r = (PortRange) entity.config().get(k);
                if (r != null && !r.isEmpty()) {
                    Integer p = r.iterator().next();
                    ports.put(p, p);
                }
            }
        }
        for (Entity child : entity.getChildren()) {
            ports.putAll(getMappedPorts(child));
        }
        return ImmutableMap.copyOf(ports);
    }

    // XXX port 22 not special cased...

    /** Returns the set of configured ports an entity is listening on. */
    public static Set<Integer> getOpenPorts(Entity entity) {
        return ImmutableSet.copyOf(getMappedPorts(entity).keySet());
    }

    /*
     * Returns the set of ports configured for the container the entity is running in
     * and also sets a configuration key and sensor for each.
     */
    public static Set<Integer> getContainerPorts(Entity entity) {
        List<Integer> entityOpenPorts = MutableList.of();
        List<Integer> openPorts = entity.config().get(DockerAttributes.DOCKER_OPEN_PORTS);
        if (openPorts != null) entityOpenPorts.addAll(openPorts);
        Map<Integer, Integer> portBindings = entity.sensors().get(DockerAttributes.DOCKER_CONTAINER_PORT_BINDINGS);
        if (portBindings != null) entityOpenPorts.addAll(portBindings.values());
        if (entityOpenPorts.size() > 0) {
            // Create config and sensor for these ports
            for (int i = 0; i < entityOpenPorts.size(); i++) {
                Integer port = entityOpenPorts.get(i);
                String name = String.format("docker.port.%d", port);
                entity.sensors().set(Sensors.newIntegerSensor(name), port);
                entity.config().set(ConfigKeys.newConfigKey(PortRange.class, name), PortRanges.fromInteger(port));
            }
        }
        return ImmutableSet.copyOf(entityOpenPorts);
    }

    public static boolean isSdnProvider(Entity dockerHost, String providerName) {
        if (dockerHost.config().get(SdnAttributes.SDN_ENABLE)) {
            Entity sdn = dockerHost.sensors().get(DockerHost.DOCKER_INFRASTRUCTURE).sensors().get(DockerInfrastructure.SDN_PROVIDER);
            if (sdn == null) return false;
            return sdn.getEntityType().getSimpleName().equalsIgnoreCase(providerName);
        } else return false;
    }

    public static final Predicate<Entity> sameInfrastructure(Entity entity) {
        Preconditions.checkNotNull(entity, "entity");
        return new SameInfrastructurePredicate(entity.getId());
    }

    public static class SameInfrastructurePredicate implements Predicate<Entity> {

        private final String id;

        public SameInfrastructurePredicate(String id) {
            this.id = Preconditions.checkNotNull(id, "id");
        }

        @Override
        public boolean apply(@Nullable Entity input) {
            // Check if entity is deployed to a DockerContainerLocation
            Optional<Location> lookup = Iterables.tryFind(input.getLocations(), Predicates.instanceOf(DockerContainerLocation.class));
            if (lookup.isPresent()) {
                DockerContainerLocation container = (DockerContainerLocation) lookup.get();
                // Only containers that are part of this infrastructure
                return id.equals(container.getOwner().getDockerHost().getInfrastructure().getId());
            } else {
                return false;
            }
        }
    };

    public static final ManagementContext.PropertiesReloadListener reloadLocationListener(ManagementContext context, LocationDefinition definition) {
        return new ReloadDockerLocation(context, definition);
    }

    public static class ReloadDockerLocation implements ManagementContext.PropertiesReloadListener {

        private final ManagementContext context;
        private final LocationDefinition definition;

        public ReloadDockerLocation(ManagementContext context, LocationDefinition definition) {
            this.context = Preconditions.checkNotNull(context, "context");
            this.definition = Preconditions.checkNotNull(definition, "definition");
        }

        @Override
        public void reloaded() {
            Location resolved = context.getLocationRegistry().resolve(definition);
            context.getLocationRegistry().updateDefinedLocation(definition);
            context.getLocationManager().manage(resolved);
        }
    };
}
