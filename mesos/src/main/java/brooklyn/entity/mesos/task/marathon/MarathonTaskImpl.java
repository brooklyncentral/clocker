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
package brooklyn.entity.mesos.task.marathon;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.net.HostAndPort;
import com.google.common.net.HttpHeaders;
import com.google.common.primitives.Ints;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import org.jclouds.compute.domain.OsFamily;
import org.jclouds.compute.domain.Processor;
import org.jclouds.compute.domain.TemplateBuilder;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.location.PortRange;
import org.apache.brooklyn.api.mgmt.LocationManager;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.feed.ConfigToAttributes;
import org.apache.brooklyn.core.location.LocationConfigKeys;
import org.apache.brooklyn.core.location.cloud.CloudLocationConfig;
import org.apache.brooklyn.core.location.dynamic.DynamicLocation;
import org.apache.brooklyn.core.sensor.DependentConfiguration;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.stock.DelegateEntity;
import org.apache.brooklyn.feed.http.HttpFeed;
import org.apache.brooklyn.feed.http.HttpPollConfig;
import org.apache.brooklyn.feed.http.HttpValueFunctions;
import org.apache.brooklyn.feed.http.JsonFunctions;
import org.apache.brooklyn.location.jclouds.JcloudsLocationConfig;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.http.HttpTool;
import org.apache.brooklyn.util.core.http.HttpToolResponse;
import org.apache.brooklyn.util.core.internal.ssh.SshTool;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Functionals;
import org.apache.brooklyn.util.net.Cidr;
import org.apache.brooklyn.util.net.Protocol;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.ssh.BashCommands;
import org.apache.brooklyn.util.text.ByteSizeStrings;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;

import brooklyn.entity.container.DockerAttributes;
import brooklyn.entity.container.DockerUtils;
import brooklyn.entity.container.docker.DockerContainer;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.mesos.MesosAttributes;
import brooklyn.entity.mesos.MesosCluster;
import brooklyn.entity.mesos.MesosSlave;
import brooklyn.entity.mesos.MesosUtils;
import brooklyn.entity.mesos.framework.MesosFramework;
import brooklyn.entity.mesos.framework.marathon.MarathonFramework;
import brooklyn.entity.mesos.task.MesosTask;
import brooklyn.entity.mesos.task.MesosTaskImpl;
import brooklyn.location.mesos.framework.marathon.MarathonTaskLocation;
import brooklyn.networking.sdn.SdnAttributes;
import brooklyn.networking.sdn.SdnProvider;
import brooklyn.networking.sdn.SdnUtils;
import brooklyn.networking.subnet.SubnetTier;

/**
 * A single Marathon task.
 */
public class MarathonTaskImpl extends MesosTaskImpl implements MarathonTask {

    private static final Logger LOG = LoggerFactory.getLogger(MesosTask.class);

    /** Valid characters for a task name. */
    public static final CharMatcher TASK_CHARACTERS = CharMatcher.anyOf("_-/")
            .or(CharMatcher.inRange('a', 'z'))
            .or(CharMatcher.inRange('0', '9'));

    /** Invalid characters for a task name. */
    public static final CharMatcher TASK_CHARACTERS_INVALID = TASK_CHARACTERS.negate();

    private transient HttpFeed httpFeed;

    @Override
    public void init() {
        super.init();

        ConfigToAttributes.apply(this, ENTITY);

        String id = getMarathonApplicationId();
        String name = Joiner.on('.').join(Lists.reverse(Splitter.on('/').omitEmptyStrings().splitToList(id)));
        sensors().set(APPLICATION_ID, id);
        sensors().set(TASK_NAME, name);

        LOG.info("Marathon task {} for: {}", id, sensors().get(ENTITY));
    }

    @Override
    public String getIconUrl() { return "classpath://container.png"; }

    private String getMarathonApplicationId() {
        String id = "/brooklyn/" + config().get(TASK_NAME).toLowerCase(Locale.ENGLISH);
        return TASK_CHARACTERS_INVALID.trimAndCollapseFrom(id, '_');
    }

    @Override
    public void connectSensors() {
        String uri = Urls.mergePaths(getFramework().sensors().get(MarathonFramework.FRAMEWORK_URL), "/v2/apps", sensors().get(APPLICATION_ID), "tasks");
        HttpFeed.Builder httpFeedBuilder = HttpFeed.builder()
                .entity(this)
                .period(500, TimeUnit.MILLISECONDS)
                .baseUri(uri)
                .credentialsIfNotNull(config().get(MesosCluster.MESOS_USERNAME), config().get(MesosCluster.MESOS_PASSWORD))
                .header("Accept", "application/json")
                .poll(new HttpPollConfig<Boolean>(SERVICE_UP)
                        .onSuccess(Functionals.chain(HttpValueFunctions.jsonContents(), JsonFunctions.walk("tasks"),
                                new Function<JsonElement, Boolean>() {
                                    @Override
                                    public Boolean apply(JsonElement input) {
                                        JsonArray tasks = input.getAsJsonArray();
                                        return tasks.size() == 1;
                                    }
                                }))
                        .onFailureOrException(Functions.constant(Boolean.FALSE)))
                .poll(new HttpPollConfig<Long>(TASK_STARTED_AT)
                        .onSuccess(Functionals.chain(HttpValueFunctions.jsonContents(), JsonFunctions.walk("tasks"),
                                new Function<JsonElement, Long>() {
                                    @Override
                                    public Long apply(JsonElement input) {
                                        JsonArray tasks = input.getAsJsonArray();
                                        for (JsonElement each : tasks) {
                                            JsonElement startedAt = each.getAsJsonObject().get("startedAt");
                                            if (startedAt != null && !startedAt.isJsonNull()) {
                                                return Time.parseDate(startedAt.getAsString()).getTime();
                                            }
                                        }
                                        return null;
                                    }
                                }))
                        .onFailureOrException(Functions.<Long>constant(-1L)))
                .poll(new HttpPollConfig<Long>(TASK_STAGED_AT)
                        .onSuccess(Functionals.chain(HttpValueFunctions.jsonContents(), JsonFunctions.walk("tasks"),
                                new Function<JsonElement, Long>() {
                                    @Override
                                    public Long apply(JsonElement input) {
                                        JsonArray tasks = input.getAsJsonArray();
                                        for (JsonElement each : tasks) {
                                            JsonElement stagedAt = each.getAsJsonObject().get("stagedAt");
                                            if (stagedAt != null && !stagedAt.isJsonNull()) {
                                                return Time.parseDate(stagedAt.getAsString()).getTime();
                                            }
                                        }
                                        return null;
                                    }
                                }))
                        .onFailureOrException(Functions.<Long>constant(-1L)))
                .poll(new HttpPollConfig<String>(Attributes.HOSTNAME)
                        .onSuccess(Functionals.chain(HttpValueFunctions.jsonContents(), JsonFunctions.walk("tasks"),
                                new Function<JsonElement, String>() {
                                    @Override
                                    public String apply(JsonElement input) {
                                        JsonArray tasks = input.getAsJsonArray();
                                        for (JsonElement each : tasks) {
                                            JsonElement host = each.getAsJsonObject().get("host");
                                            if (host != null && !host.isJsonNull()) {
                                                return host.getAsString();
                                            }
                                        }
                                        return null;
                                    }
                                }))
                        .onFailureOrException(Functions.<String>constant(null)))
                .poll(new HttpPollConfig<String>(Attributes.ADDRESS)
                        .onSuccess(Functionals.chain(HttpValueFunctions.jsonContents(), JsonFunctions.walk("tasks"),
                                new Function<JsonElement, String>() {
                                    @Override
                                    public String apply(JsonElement input) {
                                        JsonArray tasks = input.getAsJsonArray();
                                        for (JsonElement each : tasks) {
                                            JsonElement host = each.getAsJsonObject().get("host");
                                            if (host != null && !host.isJsonNull()) {
                                                try {
                                                    return InetAddress.getByName(host.getAsString()).getHostAddress();
                                                } catch (UnknownHostException uhe) {
                                                    Exceptions.propagate(uhe);
                                                }
                                            }
                                        }
                                        return null;
                                    }
                                }))
                        .onFailureOrException(Functions.<String>constant(null)));
        httpFeed = httpFeedBuilder.build();
    }

    @Override
    public void disconnectSensors() {
        if (httpFeed != null && httpFeed.isActivated()) httpFeed.destroy();
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        super.start(locations);

        ServiceStateLogic.setExpectedState(this, Lifecycle.STARTING);

        Entity entity = getRunningEntity();
        entity.sensors().set(DockerContainer.CONTAINER, this);
        entity.sensors().set(MesosAttributes.MESOS_CLUSTER, getMesosCluster());

        MarathonFramework marathon = (MarathonFramework) sensors().get(FRAMEWORK);
        marathon.sensors().get(MesosFramework.FRAMEWORK_TASKS).addMember(this);

        String id = sensors().get(APPLICATION_ID);
        Map<String, Object> flags = getMarathonFlags(entity);
        try {
            LOG.debug("Starting task {} on {} with flags: {}",
                    new Object[] { id, marathon, Joiner.on(",").withKeyValueSeparator("=").join(flags) });
            marathon.startApplication(id, flags);
        } catch (Exception e) {
            ServiceStateLogic.setExpectedState(this, Lifecycle.ON_FIRE);
            throw Exceptions.propagate(e);
        }

        // Waiting for TASK_RUNNING status and get hostname
        Task<?> running = DependentConfiguration.attributeWhenReady(this, MesosTask.TASK_STATE, Predicates.equalTo(MesosTask.TaskState.TASK_RUNNING.toString()));
        DynamicTasks.queueIfPossible(running).orSubmitAndBlock(this).asTask().getUnchecked(Duration.FIVE_MINUTES);
        Task<?> hostname = DependentConfiguration.attributeWhenReady(this, Attributes.HOSTNAME, Predicates.notNull());
        DynamicTasks.queueIfPossible(hostname).orSubmitAndBlock(this).asTask().getUnchecked(Duration.FIVE_MINUTES);
        LOG.info("Task {} running on {} successfully", id, getHostname());

        String containerId = null;
        MesosSlave slave = getMesosCluster().getMesosSlave(getHostname());
        String ps = slave.execCommand(BashCommands.sudo("docker ps --no-trunc --filter=name=mesos-* -q"));
        Iterable<String> containers = Splitter.on(CharMatcher.WHITESPACE).omitEmptyStrings().split(ps);
        for (String each : containers) {
            String env = slave.execCommand(BashCommands.sudo("docker inspect --format='{{range .Config.Env}}{{println .}}{{end}}' " + each));
            Optional<String> found = Iterables.tryFind(Splitter.on(CharMatcher.WHITESPACE).split(env), Predicates.equalTo("MARATHON_APP_ID=" + sensors().get(APPLICATION_ID)));
            if (found.isPresent()) {
                containerId = each;
                break;
            }
        }
        sensors().set(DockerContainer.CONTAINER_ID, containerId);
        entity.sensors().set(DockerContainer.CONTAINER_ID, containerId);

        createLocation(flags);

        ServiceStateLogic.setExpectedState(this, Lifecycle.RUNNING);
    }

    @Override
    public void stop() {
        Lifecycle state = sensors().get(Attributes.SERVICE_STATE_ACTUAL);
        if (Lifecycle.STOPPING.equals(state) || Lifecycle.STOPPED.equals(state)) {
            LOG.debug("Ignoring request to stop {} when it is already {}", this, state);
            LOG.trace("Duplicate stop came from: \n" + Joiner.on("\n").join(Thread.getAllStackTraces().get(Thread.currentThread())));
            return;
        }
        LOG.info("Stopping {} when its state is {}", this, sensors().get(Attributes.SERVICE_STATE_ACTUAL));
        ServiceStateLogic.setExpectedState(this, Lifecycle.STOPPING);

        deleteLocation();

        // Stop and remove the Marathon task
        String name = sensors().get(APPLICATION_ID);
        ((MarathonFramework) getFramework()).stopApplication(name);

        super.stop();
    }

    private Map<String, Object> getMarathonFlags(Entity entity) {
        MutableMap.Builder<String, Object> builder = MutableMap.builder();
        ConfigBag provisioningProperties = ConfigBag.newInstance(entity.config().get(SoftwareProcess.PROVISIONING_PROPERTIES));

        // CPU
        Double cpus = entity.config().get(MarathonTask.CPU_RESOURCES);
        if (cpus == null) cpus = config().get(MarathonTask.CPU_RESOURCES);
        if (cpus == null) {
            Integer minCores = entity.config().get(JcloudsLocationConfig.MIN_CORES);
            if (minCores == null) {
                minCores = provisioningProperties.get(JcloudsLocationConfig.MIN_CORES);
            }
            if (minCores == null) {
                TemplateBuilder template = provisioningProperties.get(JcloudsLocationConfig.TEMPLATE_BUILDER);
                if (template != null) {
                    minCores = 0;
                    for (Processor cpu : template.build().getHardware().getProcessors()) {
                        minCores = minCores + (int) cpu.getCores();
                    }
                }
            }
            if (minCores != null) {
                cpus = 1.0d * minCores;
            }
        }
        if (cpus == null) cpus = 0.25d;
        builder.put("cpus", cpus);

        // Memory
        Integer memory = entity.config().get(MarathonTask.MEMORY_RESOURCES);
        if (memory == null) memory = config().get(MarathonTask.MEMORY_RESOURCES);
        if (memory == null) {
            Integer minRam = parseMbSizeString(entity.config().get(JcloudsLocationConfig.MIN_RAM));
            if (minRam == null) {
                minRam = parseMbSizeString(provisioningProperties.get(JcloudsLocationConfig.MIN_RAM));
            }
            if (minRam == null) {
                TemplateBuilder template = provisioningProperties.get(JcloudsLocationConfig.TEMPLATE_BUILDER);
                if (template != null) {
                    minRam = template.build().getHardware().getRam();
                }
            }
            if (minRam != null) {
                memory = minRam;
            }
        }
        if (memory == null) memory = 256;
        builder.put("memory", memory);

        // Inbound ports
        Set<Integer> entityOpenPorts = MutableSet.copyOf(DockerUtils.getContainerPorts(entity));
        entityOpenPorts.addAll(DockerUtils.getOpenPorts(entity));
        if (!config().get(DockerContainer.DOCKER_USE_SSH)) {
            entityOpenPorts.remove(22);
        }
        builder.put("openPorts", Ints.toArray(entityOpenPorts));
        sensors().set(DockerAttributes.DOCKER_CONTAINER_OPEN_PORTS, ImmutableList.copyOf(entityOpenPorts));
        entity.sensors().set(DockerAttributes.DOCKER_CONTAINER_OPEN_PORTS, ImmutableList.copyOf(entityOpenPorts));

        // Direct port mappings
        // Note that the Marathon map is reversed, from container to host, with 0 indicating any host port
        Map<Integer, Integer> bindings = MutableMap.of();
        Map<Integer, Integer> marathonBindings = MutableMap.of();
        for (Integer port : entityOpenPorts) {
            marathonBindings.put(port, 0);
        }
        Map<Integer, Integer> entityBindings = entity.config().get(DockerAttributes.DOCKER_PORT_BINDINGS);
        if (entityBindings != null) {
            for (Integer host : entityBindings.keySet()) {
                bindings.put(entityBindings.get(host), host);
                marathonBindings.put(host, entityBindings.get(host));
            }
        }
        if (bindings.isEmpty()) {
            List<PortAttributeSensorAndConfigKey> entityPortConfig = entity.config().get(DockerAttributes.DOCKER_DIRECT_PORT_CONFIG);
            if (entityPortConfig != null) {
                for (PortAttributeSensorAndConfigKey key : entityPortConfig) {
                    PortRange range = entity.config().get(key);
                    if (range != null && !range.isEmpty()) {
                        Integer port = range.iterator().next();
                        if (port != null) {
                            bindings.put(port,  port);
                            marathonBindings.put(port,  port);
                        }
                    }
                }
            }
            List<Integer> entityPorts = entity.config().get(DockerAttributes.DOCKER_DIRECT_PORTS);
            if (entityPorts != null) {
                for (Integer port : entityPorts) {
                    bindings.put(port, port);
                    marathonBindings.put(port,  port);
                }
            }
        }
        sensors().set(DockerAttributes.DOCKER_CONTAINER_PORT_BINDINGS, bindings);
        entity.sensors().set(DockerAttributes.DOCKER_CONTAINER_PORT_BINDINGS, bindings);
        builder.put("portBindings", Lists.newArrayList(marathonBindings.entrySet()));

        // Set network configuration is using Calico SDN
        if (getMesosCluster().config().get(MesosCluster.SDN_ENABLE)) {
            SdnProvider provider =  ((SdnProvider) getMesosCluster().sensors().get(MesosCluster.SDN_PROVIDER));
            List<String> networks = Lists.newArrayList(entity.getApplicationId());
            Collection<String> extra = entity.config().get(SdnAttributes.NETWORK_LIST);
            if (extra != null) networks.addAll(extra);
            sensors().set(SdnAttributes.ATTACHED_NETWORKS, networks);
            entity.sensors().set(SdnAttributes.ATTACHED_NETWORKS, networks);
            Set<String> addresses = Sets.newHashSet();
            for (String networkId : networks) {
                SdnUtils.createNetwork(provider, networkId);
                InetAddress address = provider.getNextContainerAddress(networkId);
                addresses.add(address.getHostAddress().toString());
                // TODO link container to extra networks ...
                if (networkId.equals(entity.getApplicationId())) {
                    sensors().set(Attributes.SUBNET_ADDRESS, address.getHostAddress());
                    sensors().set(Attributes.SUBNET_HOSTNAME, address.getHostAddress());
                    entity.sensors().set(Attributes.SUBNET_ADDRESS, address.getHostAddress());
                    entity.sensors().set(Attributes.SUBNET_HOSTNAME, address.getHostAddress());
                    builder.put("address", address.getHostAddress());
                    builder.put("networkId", networkId);
                }
            }
            sensors().set(DockerContainer.CONTAINER_ADDRESSES, addresses);
            entity.sensors().set(DockerContainer.CONTAINER_ADDRESSES, addresses);
        }

        // Environment variables and Docker links
        Map<String, Object> environment = MutableMap.copyOf(config().get(DOCKER_CONTAINER_ENVIRONMENT));
        environment.putAll(MutableMap.copyOf(entity.config().get(DOCKER_CONTAINER_ENVIRONMENT)));
        List<Entity> links = entity.config().get(DockerAttributes.DOCKER_LINKS);
        if (links != null && links.size() > 0) {
            LOG.debug("Found links: {}", links);
            Map<String, String> extraHosts = MutableMap.of();
            for (Entity linked : links) {
                Map<String, Object> linkVars = DockerUtils.generateLinks(getRunningEntity(), linked);
                environment.putAll(linkVars);
                Optional<String> alias = DockerUtils.getContainerName(linked);
                if (alias.isPresent()) {
                    String targetAddress = DockerUtils.getTargetAddress(getRunningEntity(), linked);
                    extraHosts.put(alias.get(), targetAddress);
                }
            }
            builder.put("extraHosts", Lists.newArrayList(extraHosts.entrySet()));
        }
        sensors().set(DockerContainer.DOCKER_CONTAINER_ENVIRONMENT, ImmutableMap.copyOf(environment));
        entity.sensors().set(DockerContainer.DOCKER_CONTAINER_ENVIRONMENT, ImmutableMap.copyOf(environment));
        builder.put("environment", Lists.newArrayList(Maps.transformValues(environment, Functions.toStringFunction()).entrySet()));

        // Volumes
        Map<String, String> volumes = MutableMap.of();
        Map<String, String> mapping = entity.config().get(DockerHost.DOCKER_HOST_VOLUME_MAPPING);
        if (mapping != null) {
            for (String source : mapping.keySet()) {
                volumes.put(source, mapping.get(source));
            }
        }
        sensors().set(DockerAttributes.DOCKER_VOLUME_MAPPING, volumes);
        entity.sensors().set(DockerAttributes.DOCKER_VOLUME_MAPPING, volumes);
        builder.put("volumes", Lists.newArrayList(volumes.entrySet()));

        // URIs to copy
        List<String> uris = MutableList.copyOf(config().get(TASK_URI_LIST));
        uris.addAll(MutableList.copyOf(entity.config().get(TASK_URI_LIST)));
        sensors().set(TASK_URI_LIST, uris);
        entity.sensors().set(TASK_URI_LIST, uris);
        builder.put("uris", uris);

        // Docker config
        Optional<String> imageName = Optional.fromNullable(config().get(DOCKER_IMAGE_NAME));
        if (imageName.isPresent()) {
            // Docker image
            builder.put("imageName", imageName.get());
            builder.put("imageVersion", config().get(DOCKER_IMAGE_TAG));

            // Docker command or args
            String command = config().get(COMMAND);
            builder.putIfNotNull("command", command);
            List<String> args = MutableList.copyOf(config().get(ARGS));
            builder.putIfNotNull("args", args);
        } else {
            // OS name for image
            OsFamily os = entity.config().get(JcloudsLocationConfig.OS_FAMILY);
            if (os == null) {
                os = provisioningProperties.get(JcloudsLocationConfig.OS_FAMILY);
            }
            if (os == null) {
                TemplateBuilder template = provisioningProperties.get(JcloudsLocationConfig.TEMPLATE_BUILDER);
                if (template != null) {
                    os = template.build().getImage().getOperatingSystem().getFamily();
                }
            }
            if (os == null) {
                os = OsFamily.UBUNTU;
            }
            imageName = Optional.of(Strings.toLowerCase(os.value()));
            builder.put("imageName", "clockercentral/" + imageName.get());

            // OS version specified in regex config
            String version = entity.config().get(JcloudsLocationConfig.OS_VERSION_REGEX);
            if (version == null) {
                version = provisioningProperties.get(JcloudsLocationConfig.OS_VERSION_REGEX);
            }
            if (version == null) {
                TemplateBuilder template = provisioningProperties.get(JcloudsLocationConfig.TEMPLATE_BUILDER);
                if (template != null) {
                    version = template.build().getImage().getOperatingSystem().getVersion();
                }
            }
            if (version == null) {
                version = "latest";
            }
            builder.put("imageVersion", version);

            // Empty args
            builder.put("args", ImmutableList.of());

            // Update volume to copy root's authorized keys from the host
            volumes.put("/root/.ssh/authorized_keys", "/root/.ssh/authorized_keys");
            builder.put("volumes", Lists.newArrayList(volumes.entrySet()));
        }

        return builder.build();
    }
    
    private Long parseSizeString(Object obj, String defaultUnit) {
        if (obj == null) return null;
        return ByteSizeStrings.parse(Strings.toString(obj), defaultUnit);
    }
    
    private Integer parseMbSizeString(Object obj) {
        if (obj == null) return null;
        return (int)(parseSizeString(obj, "mb")/1000/1000);
    }

    @Override
    public Entity getRunningEntity() {
        return sensors().get(ENTITY);
    }

    @Override
    public void setRunningEntity(Entity entity) {
        sensors().set(ENTITY, entity);
    }

    public InetAddress getAddress() {
        try {
            return InetAddress.getByName(getHostname());
        } catch (UnknownHostException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public String getHostname() {
        return sensors().get(Attributes.HOSTNAME);
    }

    public Integer getSshPort() {
        String sensorValue = sensors().get(DockerAttributes.DOCKER_MAPPED_SSH_PORT);
        if (sensorValue != null) {
            HostAndPort target = HostAndPort.fromString(sensorValue);
            return target.getPort();
        } else {
            Integer sshPort = getRunningEntity().config().get(SshMachineLocation.SSH_PORT);
            return Optional.fromNullable(sshPort).or(22);
        }
    }

    @Override
    public Set<String> getPublicAddresses() {
        return ImmutableSet.of(sensors().get(Attributes.ADDRESS));
    }

    @Override
    public Set<String> getPrivateAddresses() {
        Set<String> containerAddresses = sensors().get(DockerContainer.CONTAINER_ADDRESSES);
        if (containerAddresses != null) {
            return ImmutableSet.copyOf(containerAddresses);
        } else {
            return ImmutableSet.of(sensors().get(Attributes.ADDRESS));
        }
    }

    @Override
    public MarathonTaskLocation getDynamicLocation() {
        return (MarathonTaskLocation) sensors().get(DYNAMIC_LOCATION);
    }

    public Optional<JsonElement> getApplicationJson() {
        MesosFramework framework = getFramework();
        String uri = Urls.mergePaths(framework.sensors().get(MarathonFramework.FRAMEWORK_URL), "/v2/apps", sensors().get(APPLICATION_ID));
        HttpToolResponse response = HttpTool.httpGet(
                MesosUtils.buildClient(framework),
                URI.create(uri),
                MutableMap.of(HttpHeaders.ACCEPT, "application/json"));
        if (!HttpTool.isStatusCodeHealthy(response.getResponseCode())) {
            return Optional.absent();
        } else {
            JsonElement app = HttpValueFunctions.jsonContents().apply(response);
            if (app.isJsonNull()) {
                return Optional.absent();
            } else {
                LOG.debug("Application JSON: {}", response.getContentAsString());
                return Optional.of(app);
            }
        }
    }

    @Override
    public MarathonTaskLocation createLocation(Map<String, ?> flags) {
        Entity entity = getRunningEntity();
        SubnetTier subnet = getMesosCluster().getMesosSlave(getHostname()).getSubnetTier();
        Boolean sdn = config().get(MesosCluster.SDN_ENABLE);

        // Configure the entity subnet
        LOG.info("Configuring entity {} via subnet {}", entity, subnet);
        entity.config().set(SubnetTier.PORT_FORWARDING_MANAGER, subnet.getPortForwardManager());
        entity.config().set(SubnetTier.PORT_FORWARDER, subnet.getPortForwarder());
        entity.config().set(SubnetTier.SUBNET_CIDR, Cidr.UNIVERSAL);
        DockerUtils.configureEnrichers(subnet, entity);

        // Lookup mapped ports
        List<Map.Entry<Integer, Integer>> portBindings = (List) flags.get("portBindings");
        Map<Integer, String> tcpMappings = MutableMap.of();
        Optional<JsonElement> application = getApplicationJson();
        if (application.isPresent()) {
            JsonArray tasks = application.get()
                    .getAsJsonObject().get("app")
                    .getAsJsonObject().get("tasks")
                    .getAsJsonArray();
            for (JsonElement each : tasks) {
                JsonElement ports = each.getAsJsonObject().get("ports");
                if (ports != null && !ports.isJsonNull()) {
                    JsonArray array = ports.getAsJsonArray();
                    if (array.size() > 0) {
                        for (int i = 0; i < array.size(); i++) {
                            int hostPort = array.get(i).getAsInt();
                            int containerPort = portBindings.get(i).getKey();
                            String target = (sdn ? sensors().get(Attributes.SUBNET_ADDRESS) : getId()) + ":" + containerPort;
                            tcpMappings.put(hostPort, target);
                            if (containerPort == 22) { // XXX should be a better way?
                                sensors().set(DockerAttributes.DOCKER_MAPPED_SSH_PORT,
                                        HostAndPort.fromParts(getHostname(), hostPort).toString());
                            }
                            subnet.getPortForwarder().openPortForwarding(HostAndPort.fromString(target), Optional.of(hostPort), Protocol.TCP, Cidr.UNIVERSAL);
                            subnet.getPortForwarder().openFirewallPort(entity, hostPort, Protocol.TCP, Cidr.UNIVERSAL);
                        }
                    }
                }
            }
        } else {
            throw new IllegalStateException("Cannot retrieve application details for " + sensors().get(APPLICATION_ID));
        }

        // Create our wrapper location around the task
        LocationSpec<MarathonTaskLocation> spec = LocationSpec.create(MarathonTaskLocation.class)
                .parent(getMarathonFramework().getDynamicLocation())
                .configure(flags)
                .configure(DynamicLocation.OWNER, this)
                .configure("entity", getRunningEntity())
                .configure(CloudLocationConfig.WAIT_FOR_SSHABLE, "false")
                .configure(SshMachineLocation.DETECT_MACHINE_DETAILS, false)
                .configure(SshMachineLocation.TCP_PORT_MAPPINGS, tcpMappings)
                .displayName(getShortName());
        Boolean useSsh = config().get(DockerAttributes.DOCKER_USE_SSH);
        if (useSsh) {
            spec.configure(SshMachineLocation.SSH_HOST, getHostname())
                .configure(SshMachineLocation.SSH_PORT, getSshPort())
                .configure("address", getAddress())
                .configure(LocationConfigKeys.USER, "root") // TODO from slave
                .configure(LocationConfigKeys.PASSWORD, "p4ssw0rd")
                .configure(SshTool.PROP_PASSWORD, "p4ssw0rd")
                .configure(SshTool.PROP_HOST, getHostname())
                .configure(SshTool.PROP_PORT, getSshPort())
                .configure(LocationConfigKeys.PRIVATE_KEY_DATA, (String) null) // TODO used to generate authorized_keys
                .configure(LocationConfigKeys.PRIVATE_KEY_FILE, (String) null);
        }
        MarathonTaskLocation location = getManagementContext().getLocationManager().createLocation(spec);
        sensors().set(DYNAMIC_LOCATION, location);
        sensors().set(LOCATION_NAME, location.getId());

        // Record port mappings
        for (Integer hostPort : tcpMappings.keySet()) {
            HostAndPort target = HostAndPort.fromString(tcpMappings.get(hostPort));
            subnet.getPortForwarder().openPortForwarding(location, target.getPort(), Optional.of(hostPort), Protocol.TCP, Cidr.UNIVERSAL);
        }

        LOG.info("New task location {} created", location);
        if (useSsh) {
            DockerUtils.addExtraPublicKeys(entity, location);
        }
        return location;
    }

    @Override
    public boolean isLocationAvailable() {
        return true;
    }

    @Override
    public void deleteLocation() {
        MarathonTaskLocation location = getDynamicLocation();

        if (location != null) {
            LocationManager mgr = getManagementContext().getLocationManager();
            if (mgr.isManaged(location)) {
                mgr.unmanage(location);
            }
        }

        sensors().set(DYNAMIC_LOCATION, null);
        sensors().set(LOCATION_NAME, null);
    }

    @Override
    public MarathonFramework getMarathonFramework() {
        return (MarathonFramework) sensors().get(FRAMEWORK);
    }

    static {
        RendererHints.register(ENTITY, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(TASK_STARTED_AT, RendererHints.displayValue(Time.toDateString()));
        RendererHints.register(TASK_STAGED_AT, RendererHints.displayValue(Time.toDateString()));
    }

}
