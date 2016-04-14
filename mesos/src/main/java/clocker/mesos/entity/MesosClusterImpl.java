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
package clocker.mesos.entity;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clocker.mesos.entity.framework.MesosFramework;
import clocker.mesos.entity.framework.marathon.MarathonPortForwarder;
import clocker.mesos.entity.task.MesosTask;
import clocker.mesos.location.MesosLocation;
import clocker.mesos.location.MesosResolver;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationDefinition;
import org.apache.brooklyn.api.location.LocationRegistry;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.sensor.EnricherSpec;
import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.core.entity.AbstractApplication;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityFunctions;
import org.apache.brooklyn.core.entity.EntityPredicates;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic.ComputeServiceIndicatorsFromChildrenAndMembers;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.location.BasicLocationRegistry;
import org.apache.brooklyn.core.location.LocationConfigKeys;
import org.apache.brooklyn.core.location.Locations;
import org.apache.brooklyn.core.location.cloud.CloudLocationConfig;
import org.apache.brooklyn.core.location.dynamic.DynamicLocation;
import org.apache.brooklyn.core.location.dynamic.LocationOwner;
import org.apache.brooklyn.entity.group.BasicGroup;
import org.apache.brooklyn.entity.group.DynamicGroup;
import org.apache.brooklyn.entity.group.DynamicMultiGroup;
import org.apache.brooklyn.entity.stock.DelegateEntity;
import org.apache.brooklyn.feed.http.HttpFeed;
import org.apache.brooklyn.feed.http.HttpPollConfig;
import org.apache.brooklyn.feed.http.HttpValueFunctions;
import org.apache.brooklyn.feed.http.JsonFunctions;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.internal.ssh.SshTool;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Functionals;
import org.apache.brooklyn.util.math.MathFunctions;
import org.apache.brooklyn.util.net.Cidr;
import org.apache.brooklyn.util.text.ByteSizeStrings;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;

import brooklyn.networking.subnet.SubnetTier;

/**
 * The Mesos cluster implementation.
 */
public class MesosClusterImpl extends AbstractApplication implements MesosCluster {

    private static final Logger LOG = LoggerFactory.getLogger(MesosCluster.class);

    private transient HttpFeed httpFeed;
    private transient HttpFeed scanner;

    @Override
    public void init() {
        LOG.info("Starting Mesos cluster id {}", getId());
        registerLocationResolver();
        super.init();

        Group slaves = addChild(EntitySpec.create(BasicGroup.class)
                .displayName("Mesos Slaves"));

        Group frameworks = addChild(EntitySpec.create(BasicGroup.class)
                .displayName("Mesos Frameworks"));

        DynamicGroup tasks = addChild(EntitySpec.create(DynamicGroup.class)
                .configure(DynamicGroup.ENTITY_FILTER, Predicates.and(
                        Predicates.instanceOf(MesosTask.class),
                        EntityPredicates.attributeEqualTo(MesosAttributes.MESOS_CLUSTER, this)))
                .displayName("Mesos Tasks"));

        DynamicMultiGroup applications = addChild(EntitySpec.create(DynamicMultiGroup.class)
                .configure(DynamicMultiGroup.ENTITY_FILTER, Predicates.and(
                        MesosUtils.sameCluster(this),
                        Predicates.not(EntityPredicates.applicationIdEqualTo(getApplicationId()))))
                .configure(DynamicMultiGroup.RESCAN_INTERVAL, 15L)
                .configure(DynamicMultiGroup.BUCKET_FUNCTION, new Function<Entity, String>() {
                        @Override
                        public String apply(@Nullable Entity input) {
                            return input.getApplication().getDisplayName() + ":" + input.getApplicationId();
                        }
                    })
                .configure(DynamicMultiGroup.BUCKET_SPEC, EntitySpec.create(BasicGroup.class))
                .displayName("Mesos Applications"));

        if (config().get(SDN_ENABLE) && config().get(SDN_PROVIDER_SPEC) != null) {
            EntitySpec entitySpec = EntitySpec.create(config().get(SDN_PROVIDER_SPEC));
            entitySpec.configure(MesosAttributes.MESOS_CLUSTER, this);
            Entity sdn = addChild(entitySpec);
            sensors().set(SDN_PROVIDER, sdn);
        }

        sensors().set(MESOS_SLAVES, slaves);
        sensors().set(MESOS_FRAMEWORKS, frameworks);
        sensors().set(MESOS_TASKS, tasks);
        sensors().set(MESOS_APPLICATIONS, applications);
        
        // Override the health-check: just interested in the slaves, frameworks and sdn (rather than 
        // the groups that show the tasks or apps).
        Entity sdn = sensors().get(SDN_PROVIDER);
        enrichers().add(EnricherSpec.create(ComputeServiceIndicatorsFromChildrenAndMembers.class)
                .uniqueTag(ComputeServiceIndicatorsFromChildrenAndMembers.DEFAULT_UNIQUE_TAG)
                .configure(ComputeServiceIndicatorsFromChildrenAndMembers.FROM_CHILDREN, true)
                .configure(ComputeServiceIndicatorsFromChildrenAndMembers.ENTITY_FILTER, Predicates.or(ImmutableList.of(
                        Predicates.<Entity>equalTo(slaves), 
                        Predicates.<Entity>equalTo(frameworks), 
                        (sdn == null ? Predicates.<Entity>alwaysFalse() : Predicates.equalTo(sdn))))));
    }

    @Override
    public String getIconUrl() { return "classpath://mesos-logo.png"; }

    private void registerLocationResolver() {
        // Doesn't matter if the resolver is already registered through ServiceLoader.
        // It just overwrite the existing registration (if any).
        // TODO Register separate resolvers for each infrastructure instance, unregister on unmanage.
        LocationRegistry registry = getManagementContext().getLocationRegistry();
        MesosResolver mesosResolver = new MesosResolver();
        ((BasicLocationRegistry)registry).registerResolver(mesosResolver);
        LOG.debug("Explicitly registered mesos resolver: "+mesosResolver);
    }

    @Override
    public MesosLocation getDynamicLocation() {
        return (MesosLocation) sensors().get(DYNAMIC_LOCATION);
    }

    @Override
    public boolean isLocationAvailable() {
        return getDynamicLocation() != null;
    }

    @Override
    public MesosLocation createLocation(Map<String, ?> flags) {
        String locationName = config().get(LOCATION_NAME);
        if (Strings.isBlank(locationName)) {
            String prefix = config().get(LOCATION_NAME_PREFIX);
            String suffix = config().get(LOCATION_NAME_SUFFIX);
            locationName = Joiner.on("-").skipNulls().join(prefix, getId(), suffix);
        }
        
        MesosLocation location = getManagementContext().getLocationManager().createLocation(LocationSpec.create(MesosLocation.class)
                .displayName("Mesos Cluster("+getId()+")")
                .configure(flags)
                .configure(DynamicLocation.OWNER, getProxy())
                .configure("locationName", locationName));
        
        LocationDefinition definition = location.register();
        
        sensors().set(LocationOwner.LOCATION_SPEC, definition.getSpec());
        sensors().set(LocationOwner.DYNAMIC_LOCATION, location);
        sensors().set(LocationOwner.LOCATION_NAME, locationName);
    
        LOG.info("New Mesos cluster location {} created for {}", location, this);
        return (MesosLocation) location;
    }

    @Override
    public void rebind() {
        super.rebind();

        // Reload our location definition on rebind
        ManagementContext.PropertiesReloadListener listener = sensors().get(Attributes.PROPERTIES_RELOAD_LISTENER);
        if (listener != null) {
            listener.reloaded();
        }
    }

    @Override
    public void deleteLocation() {
        MesosLocation location = getDynamicLocation();

        if (location != null) {
            location.deregister();
            Locations.unmanage(location);
        }
    
        sensors().set(LocationOwner.DYNAMIC_LOCATION, null);
        sensors().set(LocationOwner.LOCATION_NAME, null);
    }

    @Override
    public void doStart(Collection<? extends Location> locs) {
        List<Location> locations = MutableList.of();

        sensors().set(SERVICE_UP, Boolean.FALSE);
        ServiceStateLogic.setExpectedState(this, Lifecycle.STARTING);

        LOG.info("Creating new MesosLocation");
        createLocation(MutableMap.<String, Object>of());

        // Start frameworks
        try {
            Group frameworks = sensors().get(MESOS_FRAMEWORKS);
            Entities.invokeEffectorList(this, frameworks.getMembers(), Startable.START, ImmutableMap.of("locations", locations)).getUnchecked();
        } catch (Exception e) {
            LOG.warn("Error starting frameworks", e);
            ServiceStateLogic.setExpectedState(this, Lifecycle.ON_FIRE);
            Exceptions.propagate(e);
        }

        super.doStart(locations);

        connectSensors();

        ServiceStateLogic.setExpectedState(this, Lifecycle.RUNNING);
        sensors().set(SERVICE_UP, Boolean.TRUE);
    }

    /**
     * De-register our {@link MesosLocation} and its children.
     */
    @Override
    public void stop() {
        disconnectSensors();

        sensors().set(SERVICE_UP, Boolean.FALSE);

        deleteLocation();

        Duration timeout = config().get(SHUTDOWN_TIMEOUT);

        // Find all applications and stop, blocking for up to five minutes until ended
        try {
            Iterable<Entity> entities = Iterables.filter(getManagementContext().getEntityManager().getEntities(),
                    Predicates.and(MesosUtils.sameCluster(this), Predicates.not(EntityPredicates.applicationIdEqualTo(getApplicationId()))));
            Set<Application> applications = ImmutableSet.copyOf(Iterables.transform(entities, new Function<Entity, Application>() {
                @Override
                public Application apply(Entity input) { return input.getApplication(); }
            }));
            LOG.debug("Stopping applications: {}", Iterables.toString(applications));
            Entities.invokeEffectorList(this, applications, Startable.STOP).get(timeout);
        } catch (Exception e) {
            LOG.warn("Error stopping applications", e);
        }

        // Stop all framework tasks in parallel
        try {
            Group frameworks = sensors().get(MESOS_FRAMEWORKS);
            LOG.debug("Stopping framework tasks in: {}", Iterables.toString(frameworks.getMembers()));
            Entities.invokeEffectorList(this, frameworks.getMembers(), Startable.STOP).get(timeout);
        } catch (Exception e) {
            LOG.warn("Error stopping frameworks", e);
        }

        // Stop anything else left over
        // TODO Stop slave entities
        try {
            super.stop();
        } catch (Exception e) {
            LOG.warn("Error stopping children", e);
        }
    }

    public void connectSensors() {
       sensors().set(Attributes.MAIN_URI, URI.create(config().get(MESOS_URL)));

        HttpFeed.Builder httpFeedBuilder = HttpFeed.builder()
                .entity(this)
                .period(1, TimeUnit.MINUTES)
                .baseUri(sensors().get(Attributes.MAIN_URI))
                .credentialsIfNotNull(config().get(MESOS_USERNAME), config().get(MESOS_PASSWORD))
                .poll(HttpPollConfig.forSensor(SERVICE_UP)
                        .suburl("/master/health")
                        .onSuccess(HttpValueFunctions.responseCodeEquals(200))
                        .onFailureOrException(Functions.constant(Boolean.FALSE)))
                .poll(HttpPollConfig.forSensor(CLUSTER_NAME)
                        .suburl("/master/state.json")
                        .onSuccess(HttpValueFunctions.jsonContents("cluster", String.class))
                        .onFailureOrException(Functions.constant("")))
                .poll(HttpPollConfig.forSensor(CLUSTER_ID)
                        .suburl("/master/state.json")
                        .onSuccess(HttpValueFunctions.jsonContents("id", String.class))
                        .onFailureOrException(Functions.constant("")))
                .poll(HttpPollConfig.forSensor(MESOS_VERSION)
                        .suburl("/master/state.json")
                        .onSuccess(HttpValueFunctions.jsonContents("version", String.class))
                        .onFailureOrException(Functions.constant("")))
                .poll(HttpPollConfig.forSensor(CPUS_TOTAL)
                        .suburl("/system/stats.json")
                        .onSuccess(HttpValueFunctions.jsonContents("cpus_total", Integer.class))
                        .onFailureOrException(Functions.constant(-1)))
                .poll(HttpPollConfig.forSensor(LOAD_1MIN)
                        .suburl("/system/stats.json")
                        .onSuccess(HttpValueFunctions.jsonContents("avg_load_1min", Double.class))
                        .onFailureOrException(Functions.constant(-1.0d)))
                .poll(HttpPollConfig.forSensor(LOAD_5MIN)
                        .suburl("/system/stats.json")
                        .onSuccess(HttpValueFunctions.jsonContents("avg_load_5min", Double.class))
                        .onFailureOrException(Functions.constant(-1.0d)))
                .poll(HttpPollConfig.forSensor(LOAD_15MIN)
                        .suburl("/system/stats.json")
                        .onSuccess(HttpValueFunctions.jsonContents("avg_load_15min", Double.class))
                        .onFailureOrException(Functions.constant(-1.0d)))
                .poll(HttpPollConfig.forSensor(MEMORY_FREE_BYTES)
                        .suburl("/system/stats.json")
                        .onSuccess(HttpValueFunctions.jsonContents("mem_free_bytes", Long.class))
                        .onFailureOrException(Functions.constant(-1L)))
                .poll(HttpPollConfig.forSensor(MEMORY_TOTAL_BYTES)
                        .suburl("/system/stats.json")
                        .onSuccess(HttpValueFunctions.jsonContents("mem_total_bytes", Long.class))
                        .onFailureOrException(Functions.constant(-1L)));
        httpFeed = httpFeedBuilder.build();

        Duration scanInterval = config().get(SCAN_INTERVAL);
        HttpFeed.Builder scanBuilder = HttpFeed.builder()
                .entity(this)
                .period(scanInterval)
                .baseUri(sensors().get(Attributes.MAIN_URI))
                .credentialsIfNotNull(config().get(MESOS_USERNAME), config().get(MESOS_PASSWORD))
                .poll(HttpPollConfig.forSensor(MESOS_SLAVE_LIST)
                        .description("Scan Cluster Slaves")
                        .suburl("/master/state.json")
                        .onSuccess(Functionals.chain(HttpValueFunctions.jsonContents(), JsonFunctions.walk("slaves"), new Function<JsonElement, List<String>>() {
                            @Override
                            public List<String> apply(JsonElement slaves) {
                                try {
                                    return scanSlaves(slaves.getAsJsonArray());
                                } catch (UnknownHostException e) {
                                    throw Exceptions.propagate(e);
                                }
                            }
                        }))
                        .onFailureOrException(Functions.<List<String>>constant(null)))
                .poll(HttpPollConfig.forSensor(MESOS_FRAMEWORK_LIST)
                        .description("Scan Cluster Frameworks")
                        .suburl("/master/state.json")
                        .onSuccess(Functionals.chain(HttpValueFunctions.jsonContents(), JsonFunctions.walk("frameworks"), new Function<JsonElement, List<String>>() {
                            @Override
                            public List<String> apply(JsonElement frameworks) {
                                return scanFrameworks(frameworks.getAsJsonArray());
                            }
                        }))
                        .onFailureOrException(Functions.<List<String>>constant(null)));
        scanner = scanBuilder.build();
    }

    public List<String> scanFrameworks(JsonArray frameworks) {
        List<String> frameworkNames = MutableList.<String>of();
        for (int i = 0; i < frameworks.size(); i++) {
            JsonObject task = frameworks.get(i).getAsJsonObject();
            String id = task.get("id").getAsString();
            JsonElement pidObj = task.get("pid");
            String pid = null;
            if (pidObj != null && !pidObj.isJsonNull()) {
                pid = pidObj.getAsString();
            }
            String name = task.get("name").getAsString();
            String url = task.get("webui_url").getAsString();
            frameworkNames.add(name);

            Optional<Entity> entity = Iterables.tryFind(sensors().get(MESOS_FRAMEWORKS).getMembers(),
                      Predicates.compose(Predicates.equalTo(id), EntityFunctions.attribute(MesosFramework.FRAMEWORK_ID)));
            if (entity.isPresent()) continue;

            EntitySpec<? extends MesosFramework> frameworkSpec = EntitySpec.create(FRAMEWORKS.containsKey(name) ? FRAMEWORKS.get(name) : EntitySpec.create(MesosFramework.class))
                    .configure(MesosFramework.FRAMEWORK_ID, id)
                    .configure(MesosFramework.FRAMEWORK_PID, pid)
                    .configure(MesosFramework.FRAMEWORK_NAME, name)
                    .configure(MesosFramework.FRAMEWORK_URL, url)
                    .configure(MesosFramework.MESOS_CLUSTER, this)
                    .displayName(String.format("%s Framework", Strings.toInitialCapOnly(name)));
            MesosFramework added = sensors().get(MESOS_FRAMEWORKS).addMemberChild(frameworkSpec);
            added.start(ImmutableList.<Location>of());
        }
        return frameworkNames;
    }

    public List<String> scanSlaves(JsonArray slaves) throws UnknownHostException {
        List<String> slaveIds = MutableList.<String>of();
        for (int i = 0; i < slaves.size(); i++) {
            JsonObject slave = slaves.get(i).getAsJsonObject();
            boolean active = slave.get("active").getAsBoolean();
            String id = slave.get("id").getAsString();
            String hostname = slave.get("hostname").getAsString();
            Double registered = slave.get("registered_time").getAsDouble();
            Group group = sensors().get(MESOS_SLAVES);

            Optional<Entity> entity = Iterables.tryFind(group.getMembers(),
                      Predicates.compose(Predicates.equalTo(id), EntityFunctions.attribute(MesosSlave.MESOS_SLAVE_ID)));
            if (entity.isPresent()) {
                Entity found = entity.get();
                found.sensors().set(MesosSlave.SLAVE_ACTIVE, active);
                if (!active) {
                    Lifecycle state = found.sensors().get(Attributes.SERVICE_STATE_ACTUAL);
                    if (Lifecycle.ON_FIRE.equals(state) || Lifecycle.STARTING.equals(state)) {
                        continue;
                    } else if (Lifecycle.STOPPING.equals(state) || Lifecycle.STOPPED.equals(state)) {
                        group.removeMember(found);
                        group.removeChild(found);
                        Entities.unmanage(found);
                    } else {
                        ServiceStateLogic.setExpectedState(found, Lifecycle.STOPPING);
                    }
                }
            } else if (active) {
                LocationSpec<SshMachineLocation> spec = LocationSpec.create(SshMachineLocation.class)
                                .configure(SshMachineLocation.SSH_HOST, hostname)
                                .configure("address", InetAddress.getByName(hostname))
                                .displayName(hostname);
                if (config().get(MESOS_SLAVE_ACCESSIBLE)) {
                    spec.configure(CloudLocationConfig.WAIT_FOR_SSHABLE, "true")
                        .configure(SshMachineLocation.DETECT_MACHINE_DETAILS, true)
                        .configure(SshMachineLocation.SSH_PORT, config().get(MesosSlave.SLAVE_SSH_PORT))
                        .configure(LocationConfigKeys.USER, config().get(MesosSlave.SLAVE_SSH_USER))
                        .configure(LocationConfigKeys.PASSWORD, config().get(MesosSlave.SLAVE_SSH_PASSWORD))
                        .configure(SshTool.PROP_PASSWORD, config().get(MesosSlave.SLAVE_SSH_PASSWORD))
                        .configure(SshTool.PROP_PORT, config().get(MesosSlave.SLAVE_SSH_PORT))
                        .configure(LocationConfigKeys.PRIVATE_KEY_DATA, config().get(MesosSlave.SLAVE_SSH_PRIVATE_KEY_DATA))
                        .configure(LocationConfigKeys.PRIVATE_KEY_FILE, config().get(MesosSlave.SLAVE_SSH_PRIVATE_KEY_FILE));
                } else {
                    spec.configure(CloudLocationConfig.WAIT_FOR_SSHABLE, "false")
                        .configure(SshMachineLocation.DETECT_MACHINE_DETAILS, false);
                }
                SshMachineLocation machine = getManagementContext().getLocationManager().createLocation(spec);

                // Setup port forwarding
                MarathonPortForwarder portForwarder = new MarathonPortForwarder();
                portForwarder.setManagementContext(getManagementContext());

                EntitySpec<MesosSlave> slaveSpec = EntitySpec.create(MesosSlave.class)
                        .configure(MesosSlave.MESOS_SLAVE_ID, id)
                        .configure(MesosSlave.REGISTERED_AT, registered.longValue())
                        .configure(MesosSlave.MESOS_CLUSTER, this)
                        .displayName("Mesos Slave (" + hostname + ")");
                MesosSlave added = sensors().get(MESOS_SLAVES).addMemberChild(slaveSpec);
                added.sensors().set(MesosSlave.SLAVE_ACTIVE, active);
                added.sensors().set(MesosSlave.HOSTNAME, hostname);
                added.sensors().set(MesosSlave.ADDRESS, hostname);

                added.start(ImmutableList.of(machine));
                portForwarder.init(hostname, this);

                // Setup subnet tier
                SubnetTier subnetTier = added.addChild(EntitySpec.create(SubnetTier.class)
                        .configure(SubnetTier.PORT_FORWARDER, portForwarder)
                        .configure(SubnetTier.SUBNET_CIDR, Cidr.UNIVERSAL));
                Entities.start(subnetTier, ImmutableList.of(machine));
                added.sensors().set(MesosSlave.SUBNET_TIER, subnetTier);
            }

            if (active) slaveIds.add(id);
        }
        return slaveIds;
    }

    @Override
    public MesosSlave getMesosSlave(String hostname) {
        Collection<Entity> slaves = sensors().get(MESOS_SLAVES).getMembers();
        Optional<Entity> found = Iterables.tryFind(slaves, Predicates.or(
                EntityPredicates.attributeEqualTo(MesosSlave.HOSTNAME, hostname),
                EntityPredicates.attributeEqualTo(MesosSlave.ADDRESS, hostname)));
        if (found.isPresent()) {
            return (MesosSlave) found.get();
        } else {
            throw new IllegalStateException("Cannot find slave for host: " + hostname);
        }
    }


    public void disconnectSensors() {
        if (httpFeed != null && httpFeed.isActivated()) httpFeed.destroy();
        if (scanner != null && scanner.isActivated()) scanner.destroy();
    }

    static {
        RendererHints.register(MESOS_SLAVES, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(MESOS_FRAMEWORKS, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(MESOS_TASKS, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(MESOS_APPLICATIONS, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(SDN_PROVIDER, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));

        RendererHints.register(START_TIME, RendererHints.displayValue(Time.toDateString()));

        RendererHints.register(LOAD_1MIN, RendererHints.displayValue(MathFunctions.percent(2)));
        RendererHints.register(LOAD_5MIN, RendererHints.displayValue(MathFunctions.percent(2)));
        RendererHints.register(LOAD_15MIN, RendererHints.displayValue(MathFunctions.percent(2)));

        RendererHints.register(MEMORY_FREE_BYTES, RendererHints.displayValue(ByteSizeStrings.iso()));
        RendererHints.register(MEMORY_TOTAL_BYTES, RendererHints.displayValue(ByteSizeStrings.iso()));
    }

}
