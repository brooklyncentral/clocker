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

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clocker.docker.entity.container.DockerContainer;
import clocker.docker.location.DockerContainerLocation;
import clocker.docker.networking.entity.sdn.util.SdnAttributes;

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
import com.google.common.net.HostAndPort;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.PortRange;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampConstants;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.core.config.render.RendererHints.Hint;
import org.apache.brooklyn.core.config.render.RendererHints.NamedActionWithUrl;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityAndAttribute;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.core.objs.BrooklynObjectInternal.ConfigurationSupportInternal;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.database.DatastoreMixins;
import org.apache.brooklyn.entity.messaging.MessageBroker;
import org.apache.brooklyn.entity.nosql.couchbase.CouchbaseCluster;
import org.apache.brooklyn.entity.nosql.couchbase.CouchbaseNode;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.apache.brooklyn.entity.webapp.WebAppServiceConstants;
import org.apache.brooklyn.location.jclouds.JcloudsLocationConfig;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.ssh.SshTasks;
import org.apache.brooklyn.util.core.task.system.ProcessTaskWrapper;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.text.Strings;

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

    public static final String DEFAULT_DOCKER_CONTAINER_NAME_FORMAT = "clocker-%2$02x";
    public static final String DEFAULT_DOCKER_HOST_NAME_FORMAT = "docker-%1$s";

    public static final String UBUNTU_DOCKERFILE = "classpath://clocker/docker/entity/container/ubuntu/Dockerfile";
    public static final String UBUNTU_NETWORKING_DOCKERFILE = "classpath://clocker/docker/entity/container/ubuntu/NetworkingDockerfile";
    public static final String UBUNTU_USES_JAVA_DOCKERFILE = "classpath://clocker/docker/entity/container/ubuntu/UsesJavaDockerfile";
    

    public static final String CENTOS_DOCKERFILE = "classpath://clocker/docker/entity/container/centos/Dockerfile";
    public static final String COREOS_DOCKERFILE = "classpath://clocker/docker/entity/container/coreos/Dockerfile";

    public static final String SSHD_DOCKERFILE = "classpath://clocker/docker/entity/container/SshdDockerfile";

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
                entity.enrichers().add(subnetTier.uriTransformingEnricher(
                        EntityAndAttribute.create(entity, sensor), target));
                Set<Hint<?>> hints = RendererHints.getHintsFor(sensor);
                for (Hint<?> hint : hints) {
                    RendererHints.register(target, (NamedActionWithUrl) hint);
                }
                LOG.debug("Mapped URL sensor: entity={}, origin={}, mapped={}", new Object[] {entity, sensor.getName(), target.getName()});
            } else if (sensor.getName().matches("docker\\.port\\.[0-9]+") ||
                    PortAttributeSensorAndConfigKey.class.isAssignableFrom(sensor.getClass())) {
                AttributeSensor<String> target = DockerUtils.mappedPortSensor(sensor);
                entity.enrichers().add(subnetTier.hostAndPortTransformingEnricher(
                        EntityAndAttribute.create(entity, sensor), target));
                LOG.debug("Mapped port sensor: entity={}, origin={}, mapped={}", new Object[] {entity, sensor.getName(), target.getName()});
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
        long hash = HashGenerator.builder()
                .add(simpleName)
                .add(version)
                .add(dockerfile)
                .add(entity, VanillaSoftwareProcess.INSTALL_UNIQUE_LABEL)
                .add(entity, SoftwareProcess.DOWNLOAD_URL.getConfigKey())
                .add(entity, SoftwareProcess.PRE_INSTALL_FILES)
                .add(entity, SoftwareProcess.PRE_INSTALL_TEMPLATES)
                .add(entity, VanillaSoftwareProcess.PRE_INSTALL_COMMAND)
                .add(entity, VanillaSoftwareProcess.INSTALL_COMMAND)
                .add(entity, SoftwareProcess.SHELL_ENVIRONMENT)
                .build();
        
        return Identifiers.makeIdFromHash(hash).toLowerCase(Locale.ENGLISH);
    }

    public static String randomImageName() {
        return Identifiers.makeRandomId(12).toLowerCase(Locale.ENGLISH);
    }
    
    private static class HashGenerator {
        private long hash;

        static HashGenerator builder() {
            return new HashGenerator();
        }
        
        private HashGenerator() {
        }

        public HashGenerator add(String val) {
            hash = hash * 31 + (val == null ? 0 : Hashing.md5().hashString(val, Charsets.UTF_8).asLong());
            return this;
        }
        
        public HashGenerator add(Entity entity, ConfigKey<?> key) {
            Maybe<?> value = ((ConfigurationSupportInternal)entity.config()).getNonBlocking(key);
            if (value.isPresent()) hash = hash*31 + (value.get()==null ? 0 : value.get().hashCode());
            return this;
        }
        
        public long build() {
            return hash;
        }
    }
    
    public static Optional<String> getContainerName(Entity target) {
        Optional<String> unique = getUniqueContainerName(target);
        if (unique.isPresent()) {
            String name = unique.get();
            String suffix = "_" + target.getId();
            return Optional.of(Strings.removeFromEnd(name, suffix));
        } else {
            return Optional.absent();
        }
    }

    public static Optional<String> getUniqueContainerName(Entity target) {
        return Optional.fromNullable(target.sensors().get(DockerContainer.DOCKER_CONTAINER_NAME))
                .or(Optional.fromNullable(target.config().get(DockerContainer.DOCKER_CONTAINER_NAME)))
                .or(Optional.fromNullable(getContainerNameFromPlan(target)))
                .transform(DockerUtils.ALLOWED);
    }

    private static String getContainerNameFromPlan(Entity target) {
        String planId = target.config().get(BrooklynCampConstants.PLAN_ID);
        if (planId != null) {
            // Plan IDs are not unique even in a single application
            return planId + "_" + target.getId();
        } else {
            return null;
        }
    }

    /* Generate the address to use to talk to another target entity. */
    public static String getTargetAddress(Entity source, Entity target) {
        boolean local = source.sensors().get(SoftwareProcess.PROVISIONING_LOCATION).equals(target.sensors().get(SoftwareProcess.PROVISIONING_LOCATION));
        List networks = target.sensors().get(SdnAttributes.ATTACHED_NETWORKS);
        if (local && (networks != null && networks.size() > 0)) {
            return target.sensors().get(Attributes.SUBNET_ADDRESS);
        } else {
            return target.sensors().get(Attributes.ADDRESS);
        }
    }

    /* Generate the list of link environment variables. */
    public static Map<String, Object> generateLinks(Entity source, Entity target) {
        Entities.waitForServiceUp(target);
        Optional<String> from = DockerUtils.getContainerName(source);
        Optional<String> to = DockerUtils.getContainerName(target);
        boolean local = source.sensors().get(SoftwareProcess.PROVISIONING_LOCATION).equals(target.sensors().get(SoftwareProcess.PROVISIONING_LOCATION));
        List networks = target.sensors().get(SdnAttributes.ATTACHED_NETWORKS);
        if (to.isPresent()) {
            String address = DockerUtils.getTargetAddress(source, target);
            Map<Integer, Integer> ports = MutableMap.of();
            Set<Integer> containerPorts = MutableSet.copyOf(target.sensors().get(DockerAttributes.DOCKER_CONTAINER_OPEN_PORTS));
            if (containerPorts.size() > 0) {
                for (Integer port : containerPorts) {
                    AttributeSensor<String> sensor = Sensors.newStringSensor(String.format("mapped.docker.port.%d", port));
                    String hostAndPort = target.sensors().get(sensor);
                    if ((local && (networks != null && networks.size() > 0)) || hostAndPort == null) {
                        ports.put(port, port);
                    } else {
                        ports.put(HostAndPort.fromString(hostAndPort).getPort(), port);
                    }
                }
            } else {
                ports = ImmutableMap.copyOf(DockerUtils.getMappedPorts(target));
            }
            Map<String, Object> env = MutableMap.of();
            for (Integer port : ports.keySet()) {
                Integer containerPort = ports.get(port);
                env.put(String.format("%S_NAME", to.get()), String.format("/%s/%s", from.or(source.getId()), to.get()));
                env.put(String.format("%S_PORT", to.get()), String.format("tcp://%s:%d", address, port));
                env.put(String.format("%S_PORT_%d_TCP", to.get(), containerPort), String.format("tcp://%s:%d", address, port));
                env.put(String.format("%S_PORT_%d_TCP_ADDR", to.get(), containerPort), address);
                env.put(String.format("%S_PORT_%d_TCP_PORT", to.get(), containerPort), port);
                env.put(String.format("%S_PORT_%d_TCP_PROTO", to.get(), containerPort), "tcp");
            }
            LOG.debug("Links for {}: {}", to, Joiner.on(" ").withKeyValueSeparator("=").join(env));
            return env;
        } else {
            LOG.warn("Cannot generate links for {}: no name specified", target);
            return ImmutableMap.<String, Object>of();
        }
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

    /** Returns the set of configured ports an entity is listening on. */
    public static Set<Integer> getOpenPorts(Entity entity) {
        Set<Integer> ports = MutableSet.of(22); // Default for SSH, may be removed
        ports.addAll(getMappedPorts(entity).keySet());
        return ImmutableSet.copyOf(ports);
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

    public static void addExtraPublicKeys(Entity entity, SshMachineLocation location) {
        String extraPublicKey = location.config().get(JcloudsLocationConfig.EXTRA_PUBLIC_KEY_DATA_TO_AUTH);
        if (extraPublicKey == null) {
            // Custom location config doesn't get passed through because locations are cached for performance reasons.
            // As a fallback check the entity config.
            extraPublicKey = entity.config().get(JcloudsLocationConfig.EXTRA_PUBLIC_KEY_DATA_TO_AUTH);
        }
        if (extraPublicKey != null) {
            LOG.info(location + ": Adding public key " + extraPublicKey);
            String cmd = "mkdir -p ~/.ssh && cat <<EOF >> ~/.ssh/authorized_keys\n" + extraPublicKey + "\nEOF\n";
            ProcessTaskWrapper<Integer> task = SshTasks.newSshExecTaskFactory(location, cmd)
                .summary("Add public key")
                .requiringExitCodeZero().newTask();
            BrooklynTaskTags.markInessential(task);
            DynamicTasks.queueIfPossible(task).orSubmitAsync(entity);
        }
    };
}
