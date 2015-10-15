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
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.LocationManager;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampConstants;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.feed.ConfigToAttributes;
import org.apache.brooklyn.core.location.LocationConfigKeys;
import org.apache.brooklyn.core.location.dynamic.DynamicLocation;
import org.apache.brooklyn.core.sensor.DependentConfiguration;
import org.apache.brooklyn.feed.http.HttpFeed;
import org.apache.brooklyn.feed.http.HttpPollConfig;
import org.apache.brooklyn.feed.http.HttpValueFunctions;
import org.apache.brooklyn.feed.http.JsonFunctions;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.internal.ssh.SshTool;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Functionals;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.time.Time;

import brooklyn.entity.mesos.framework.MesosFramework;
import brooklyn.entity.mesos.framework.marathon.MarathonFramework;
import brooklyn.entity.mesos.task.MesosTask;
import brooklyn.entity.mesos.task.MesosTaskImpl;
import brooklyn.location.mesos.framework.marathon.MarathonTaskLocation;

/**
 * A single Marathon task.
 */
public class MarathonTaskImpl extends MesosTaskImpl implements MarathonTask {

    private static final Logger LOG = LoggerFactory.getLogger(MesosTask.class);

    private transient HttpFeed httpFeed;

    @Override
    public void init() {
        super.init();

        String name = Optional.fromNullable(config().get(BrooklynCampConstants.PLAN_ID))
                .or(Optional.fromNullable(config().get(MarathonTask.APPLICATION_ID)))
                .or(Optional.fromNullable(config().get(MesosTask.TASK_NAME)))
                .or(getId());
        sensors().set(MesosTask.TASK_NAME, name);

        ConfigToAttributes.apply(this, ENTITY);

        LOG.info("Marathon task {} for: {}", name, sensors().get(ENTITY));
    }

    @Override
    public void connectSensors() {
        final String name = sensors().get(TASK_NAME);
        String uri = Urls.mergePaths(getFramework().sensors().get(MarathonFramework.FRAMEWORK_URL), "/v2/apps", name, "tasks");
        HttpFeed.Builder httpFeedBuilder = HttpFeed.builder()
                .entity(this)
                .period(500, TimeUnit.MILLISECONDS)
                .baseUri(uri)
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
                .poll(new HttpPollConfig<Date>(TASK_STARTED_AT)
                        .onSuccess(Functionals.chain(HttpValueFunctions.jsonContents(), JsonFunctions.walk("tasks"),
                                new Function<JsonElement, Date>() {
                                    @Override
                                    public Date apply(JsonElement input) {
                                        JsonArray tasks = input.getAsJsonArray();
                                        for (JsonElement each : tasks) {
                                            String startedAt = each.getAsJsonObject().get("startedAt").getAsString();
                                            return Time.parseDate(startedAt);
                                        }
                                        return null;
                                    }
                                }))
                        .onFailureOrException(Functions.<Date>constant(null)))
                .poll(new HttpPollConfig<Date>(TASK_STAGED_AT)
                        .onSuccess(Functionals.chain(HttpValueFunctions.jsonContents(), JsonFunctions.walk("tasks"),
                                new Function<JsonElement, Date>() {
                                    @Override
                                    public Date apply(JsonElement input) {
                                        JsonArray tasks = input.getAsJsonArray();
                                        for (JsonElement each : tasks) {
                                            String stagedAt = each.getAsJsonObject().get("stagedAt").getAsString();
                                            return Time.parseDate(stagedAt);
                                        }
                                        return null;
                                    }
                                }))
                        .onFailureOrException(Functions.<Date>constant(null)))
                .poll(new HttpPollConfig<String>(Attributes.HOSTNAME)
                        .onSuccess(Functionals.chain(HttpValueFunctions.jsonContents(), JsonFunctions.walk("tasks"),
                                new Function<JsonElement, String>() {
                                    @Override
                                    public String apply(JsonElement input) {
                                        JsonArray tasks = input.getAsJsonArray();
                                        for (JsonElement each : tasks) {
                                            String host = each.getAsJsonObject().get("host").getAsString();
                                            return host;
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
                                            String host = each.getAsJsonObject().get("host").getAsString();
                                            try {
                                                return InetAddress.getByName(host).getHostAddress();
                                            } catch (UnknownHostException uhe) {
                                                Exceptions.propagate(uhe);
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
        if (httpFeed != null && httpFeed.isRunning()) httpFeed.stop();
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        super.start(locations);

        MarathonFramework marathon = (MarathonFramework) sensors().get(FRAMEWORK);
        if (marathon == null) throw new IllegalStateException("Cannot start without a Marathon framework");
        marathon.sensors().get(MesosFramework.FRAMEWORK_TASKS).addMember(this);

        String name = sensors().get(MesosTask.TASK_NAME);
        try {
            Map<String, Object> flags = getMarathonFlags();
            LOG.debug("Starting task {} on {} with flags: {}",
                    new Object[] { name, marathon, Joiner.on(",").withKeyValueSeparator("=").join(flags) });
            marathon.startApplication(name, flags);
        } catch (Exception e) {
            Exceptions.propagate(e);
        }

        String hostname = DependentConfiguration.waitInTaskForAttributeReady(this, Attributes.HOSTNAME, Predicates.notNull());
        LOG.info("Task {} running on {} successfully", name, hostname);

        Map<String, ?> flags = MutableMap.<String, Object>of();
        createLocation(flags);
    }

    @Override
    public void stop() {
        deleteLocation();

        super.stop();
    }

    private Map<String, Object> getMarathonFlags() {
        MutableMap.Builder<String, Object> builder = MutableMap.builder();

        // CPU
        Double cpus = config().get(CPU_RESOURCES);
        builder.putIfNotNull("cpus", cpus);

        // Memory
        Integer memory = config().get(MEMORY_RESOURCES);
        builder.putIfNotNull("memory", memory);

        // Inbound ports
        List<Integer> openPorts = ImmutableList.copyOf(config().get(DOCKER_OPEN_PORTS));
        builder.put("openPorts", openPorts);
        Map<Integer, Integer> portBindings = MutableMap.copyOf(config().get(DOCKER_PORT_BINDINGS));
        List<Integer> directPorts = ImmutableList.copyOf(config().get(DOCKER_DIRECT_PORTS));
        for (Integer port : directPorts) {
            portBindings.put(port, port);
        }
        builder.put("portBindings", Lists.newArrayList(portBindings.entrySet()));

        // Environment variables
        Map<String, Object> environment = MutableMap.copyOf(config().get(DOCKER_CONTAINER_ENVIRONMENT));
        builder.put("environment", Lists.newArrayList(environment.entrySet()));

        // Docker command or args
        String command = config().get(COMMAND);
        builder.putIfNotNull("command", command);
        List<String> args = config().get(ARGS);
        builder.putIfNotNull("args", args);

        // Docker image
        builder.put("imageName", config().get(DOCKER_IMAGE_NAME));
        builder.put("imageVersion", config().get(DOCKER_IMAGE_TAG));

        return builder.build();
    }

    @Override
    public Entity getRunningEntity() {
        return sensors().get(ENTITY);
    }

    @Override
    public void setRunningEntity(Entity entity) {
        sensors().set(ENTITY, entity);
    }

    @Override
    public String getHostname() {
        return sensors().get(Attributes.HOSTNAME);
    }

    @Override
    public Set<String> getPublicAddresses() {
        return null;
    }

    @Override
    public Set<String> getPrivateAddresses() {
        return null;
    }

    @Override
    public MarathonTaskLocation getDynamicLocation() {
        return (MarathonTaskLocation) sensors().get(DYNAMIC_LOCATION);
    }

    @Override
    public MarathonTaskLocation createLocation(Map<String, ?> flags) {
        // Create our wrapper location around the task
        LocationSpec<MarathonTaskLocation> spec = LocationSpec.create(MarathonTaskLocation.class)
                .parent(getMarathonFramework().getDynamicLocation())
                .configure(flags)
                .configure(DynamicLocation.OWNER, this)
                .configure("entity",  getRunningEntity())
                .configureIfNotNull(SshMachineLocation.SSH_HOST, getHostname())
                .configure(LocationConfigKeys.USER, "root")
                .configure(LocationConfigKeys.PASSWORD, "p4ssw0rd")
                .configure(SshTool.PROP_PASSWORD, "p4ssw0rd")
                .configure(LocationConfigKeys.PRIVATE_KEY_DATA, (String) null)
                .configure(LocationConfigKeys.PRIVATE_KEY_FILE, (String) null)
//                .configure(CloudLocationConfig.WAIT_FOR_SSHABLE, "false")
                .configure(SshMachineLocation.DETECT_MACHINE_DETAILS, false)
                .configure(SshMachineLocation.SSH_HOST, getHostname())
//                .configure(SshMachineLocation.TCP_PORT_MAPPINGS, Map)
//                .configure(SshMachineLocation.TCP_PORT_MAPPINGS, Map)
//                .configure(JcloudsLocation.USE_PORT_FORWARDING, true)
//                .configure(JcloudsLocation.PORT_FORWARDER, subnetTier.getPortForwarderExtension())
//                .configure(JcloudsLocation.PORT_FORWARDING_MANAGER, subnetTier.getPortForwardManager())
//                .configure(JcloudsPortforwardingSubnetLocation.PORT_FORWARDER, subnetTier.getPortForwarder())
//                .configure(SubnetTier.SUBNET_CIDR, Cidr.CLASS_B)
                .displayName(getShortName());
        MarathonTaskLocation location = getManagementContext().getLocationManager().createLocation(spec);

        sensors().set(DYNAMIC_LOCATION, location);
        sensors().set(LOCATION_NAME, location.getId());

        LOG.info("New task location {} created", location);
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

}
