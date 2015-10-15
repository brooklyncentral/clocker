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
package brooklyn.entity.mesos;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.Resources;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationDefinition;
import org.apache.brooklyn.api.location.LocationRegistry;
import org.apache.brooklyn.api.mgmt.LocationManager;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityFunctions;
import org.apache.brooklyn.core.entity.EntityPredicates;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.location.BasicLocationDefinition;
import org.apache.brooklyn.core.location.BasicLocationRegistry;
import org.apache.brooklyn.core.location.dynamic.LocationOwner;
import org.apache.brooklyn.entity.group.BasicGroup;
import org.apache.brooklyn.entity.group.DynamicGroup;
import org.apache.brooklyn.entity.group.DynamicMultiGroup;
import org.apache.brooklyn.entity.stock.BasicStartableImpl;
import org.apache.brooklyn.entity.stock.DelegateEntity;
import org.apache.brooklyn.feed.function.FunctionFeed;
import org.apache.brooklyn.feed.function.FunctionPollConfig;
import org.apache.brooklyn.feed.http.HttpFeed;
import org.apache.brooklyn.feed.http.HttpPollConfig;
import org.apache.brooklyn.feed.http.HttpValueFunctions;
import org.apache.brooklyn.feed.http.JsonFunctions;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.math.MathFunctions;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.text.ByteSizeStrings;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;

import brooklyn.entity.container.DockerUtils;
import brooklyn.entity.mesos.framework.MesosFramework;
import brooklyn.entity.mesos.task.MesosTask;
import brooklyn.location.mesos.MesosLocation;
import brooklyn.location.mesos.MesosResolver;

/**
 * The Mesos cluster implementation.
 */
public class MesosClusterImpl extends BasicStartableImpl implements MesosCluster {

    private static final Logger LOG = LoggerFactory.getLogger(MesosCluster.class);

    private transient HttpFeed httpFeed;
    private transient FunctionFeed frameworkScan;

    @Override
    public void init() {
        LOG.info("Starting Mesos cluster id {}", getId());
        registerLocationResolver();
        super.init();

        DynamicGroup frameworks = addChild(EntitySpec.create(DynamicGroup.class)
                .displayName("Mesos Frameworks"));

        DynamicGroup tasks = addChild(EntitySpec.create(DynamicGroup.class)
                .configure(DynamicGroup.ENTITY_FILTER, Predicates.and(Predicates.instanceOf(MesosTask.class), EntityPredicates.attributeEqualTo(MesosAttributes.MESOS_CLUSTER, this)))
                .configure(DynamicGroup.MEMBER_DELEGATE_CHILDREN, true)
                .displayName("Mesos Tasks"));

        DynamicMultiGroup applications = addChild(EntitySpec.create(DynamicMultiGroup.class)
                .configure(DynamicMultiGroup.ENTITY_FILTER, MesosUtils.sameCluster(this))
                .configure(DynamicMultiGroup.RESCAN_INTERVAL, 15L)
                .configure(DynamicMultiGroup.BUCKET_FUNCTION, new Function<Entity, String>() {
                        @Override
                        public String apply(@Nullable Entity input) {
                            return input.getApplication().getDisplayName() + ":" + input.getApplicationId();
                        }
                    })
                .configure(DynamicMultiGroup.BUCKET_SPEC, EntitySpec.create(BasicGroup.class)
                        .configure(BasicGroup.MEMBER_DELEGATE_CHILDREN, true))
                .displayName("Mesos Applications"));

        if (Entities.isManaged(this)) {
            Entities.manage(frameworks);
            Entities.manage(tasks);
            Entities.manage(applications);
        }

        sensors().set(MESOS_FRAMEWORKS, frameworks);
        sensors().set(MESOS_TASKS, tasks);
        sensors().set(MESOS_APPLICATIONS, applications);

        sensors().set(Attributes.MAIN_URI, URI.create(config().get(MESOS_URL)));
    }

    private void registerLocationResolver() {
        // Doesn't matter if the resolver is already registered through ServiceLoader.
        // It just overwrite the existing registration (if any).
        // TODO Register separate resolvers for each infrastructure instance, unregister on unmanage.
        LocationRegistry registry = getManagementContext().getLocationRegistry();
        MesosResolver mesosResolver = new MesosResolver();
        ((BasicLocationRegistry)registry).registerResolver(mesosResolver);
        if (LOG.isDebugEnabled()) LOG.debug("Explicitly registered mesos resolver: "+mesosResolver);
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
        LocationDefinition check = getManagementContext().getLocationRegistry().getDefinedLocationByName(locationName);
        if (check != null) {
            throw new IllegalStateException("Location " + locationName + " is already defined: " + check);
        }

        String locationSpec = String.format(MesosResolver.MESOS_CLUSTER_SPEC, getId()) + String.format(":(name=\"%s\")", locationName);
        sensors().set(LOCATION_SPEC, locationSpec);
        LocationDefinition definition = new BasicLocationDefinition(locationName, locationSpec, flags);
        Location location = getManagementContext().getLocationRegistry().resolve(definition);
        getManagementContext().getLocationRegistry().updateDefinedLocation(definition);
        getManagementContext().getLocationManager().manage(location);

        ManagementContext.PropertiesReloadListener listener = DockerUtils.reloadLocationListener(getManagementContext(), definition);
        getManagementContext().addPropertiesReloadListener(listener);
        sensors().set(Attributes.PROPERTIES_RELOAD_LISTENER, listener);

        sensors().set(LocationOwner.LOCATION_DEFINITION, definition);
        sensors().set(LocationOwner.DYNAMIC_LOCATION, location);
        sensors().set(LocationOwner.LOCATION_NAME, location.getId());

        LOG.info("New Mesos location {} created", location);
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
            LocationManager mgr = getManagementContext().getLocationManager();
            if (mgr.isManaged(location)) {
                mgr.unmanage(location);
            }
            final LocationDefinition definition = sensors().get(LocationOwner.LOCATION_DEFINITION);
            if (definition != null) {
                getManagementContext().getLocationRegistry().removeDefinedLocation(definition.getId());
            }
        }
        ManagementContext.PropertiesReloadListener listener = sensors().get(Attributes.PROPERTIES_RELOAD_LISTENER);
        if (listener != null) {
            getManagementContext().removePropertiesReloadListener(listener);
        }

        sensors().set(LocationOwner.LOCATION_DEFINITION, null);
        sensors().set(LocationOwner.DYNAMIC_LOCATION, null);
        sensors().set(LocationOwner.LOCATION_NAME, null);
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        sensors().set(SERVICE_UP, Boolean.FALSE);

        LOG.info("Creating new MesosLocation");
        createLocation(MutableMap.<String, Object>of());

        super.start(locations);

        // Start frameworks
        try {
            DynamicGroup frameworks = sensors().get(MESOS_FRAMEWORKS);
            Entities.invokeEffectorList(this, frameworks.getMembers(), Startable.START, ImmutableMap.of("locations", locations)).getUnchecked();
        } catch (Exception e) {
            LOG.warn("Error starting frameworks", e);
            Exceptions.propagate(e);
        }

        connectSensors();
    }

    /**
     * De-register our {@link MesosLocation} and its children.
     */
    @Override
    public void stop() {
        disconnectSensors();

        sensors().set(SERVICE_UP, Boolean.FALSE);
        Duration timeout = config().get(SHUTDOWN_TIMEOUT);

        // Find all applications and stop, blocking for up to five minutes until ended
        try {
            Iterable<Entity> entities = Iterables.filter(getManagementContext().getEntityManager().getEntities(), MesosUtils.sameCluster(this));
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
            DynamicGroup frameworks = sensors().get(MESOS_FRAMEWORKS);
            LOG.debug("Stopping framework tasks in: {}", Iterables.toString(frameworks.getMembers()));
            Entities.invokeEffectorList(this, frameworks.getMembers(), Startable.STOP).get(timeout);
        } catch (Exception e) {
            LOG.warn("Error stopping frameworks", e);
        }

        // Stop anything else left over
        super.stop();

        deleteLocation();
    }

    public void connectSensors() {
        HttpFeed.Builder httpFeedBuilder = HttpFeed.builder()
                .entity(this)
                .period(500, TimeUnit.MILLISECONDS)
                .baseUri(sensors().get(Attributes.MAIN_URI))
                .poll(new HttpPollConfig<Boolean>(SERVICE_UP)
                        .suburl("/master/health")
                        .onSuccess(HttpValueFunctions.responseCodeEquals(200))
                        .onFailureOrException(Functions.constant(Boolean.FALSE)))
                .poll(new HttpPollConfig<String>(CLUSTER_NAME)
                        .suburl("/master/state.json")
                        .onSuccess(HttpValueFunctions.jsonContents("cluster", String.class))
                        .onFailureOrException(Functions.constant("")))
                .poll(new HttpPollConfig<String>(CLUSTER_ID)
                        .suburl("/master/state.json")
                        .onSuccess(HttpValueFunctions.jsonContents("id", String.class))
                        .onFailureOrException(Functions.constant("")))
                .poll(new HttpPollConfig<String>(MESOS_VERSION)
                        .suburl("/master/state.json")
                        .onSuccess(HttpValueFunctions.jsonContents("version", String.class))
                        .onFailureOrException(Functions.constant("")))
                .poll(new HttpPollConfig<Integer>(CPUS_TOTAL)
                        .suburl("/system/stats.json")
                        .onSuccess(HttpValueFunctions.jsonContents("cpus_total", Integer.class))
                        .onFailureOrException(Functions.constant(-1)))
                .poll(new HttpPollConfig<Double>(LOAD_1MIN)
                        .suburl("/system/stats.json")
                        .onSuccess(HttpValueFunctions.jsonContents("avg_load_1min", Double.class))
                        .onFailureOrException(Functions.constant(-1.0d)))
                .poll(new HttpPollConfig<Double>(LOAD_5MIN)
                        .suburl("/system/stats.json")
                        .onSuccess(HttpValueFunctions.jsonContents("avg_load_5min", Double.class))
                        .onFailureOrException(Functions.constant(-1.0d)))
                .poll(new HttpPollConfig<Double>(LOAD_15MIN)
                        .suburl("/system/stats.json")
                        .onSuccess(HttpValueFunctions.jsonContents("avg_load_15min", Double.class))
                        .onFailureOrException(Functions.constant(-1.0d)))
                .poll(new HttpPollConfig<Long>(MEMORY_FREE_BYTES)
                        .suburl("/system/stats.json")
                        .onSuccess(HttpValueFunctions.jsonContents("mem_free_bytes", Long.class))
                        .onFailureOrException(Functions.constant(-1L)))
                .poll(new HttpPollConfig<Long>(MEMORY_TOTAL_BYTES)
                        .suburl("/system/stats.json")
                        .onSuccess(HttpValueFunctions.jsonContents("mem_total_bytes", Long.class))
                        .onFailureOrException(Functions.constant(-1L)));
        httpFeed = httpFeedBuilder.build();

        Duration scanInterval = config().get(SCAN_INTERVAL);
        FunctionFeed.Builder frameworkScanBuilder = FunctionFeed.builder()
                .entity(this)
                .poll(new FunctionPollConfig<Object, Void>(MESOS_FRAMEWORK_SCAN)
                        .period(scanInterval)
                        .description("Scan Frameworks")
                        .callable(new Callable<Void>() {
                                @Override
                                public Void call() throws Exception {
                                    scanFrameworks();
                                    return null;
                                }
                            })
                        .onFailureOrException(Functions.<Void>constant(null)));
        frameworkScan = frameworkScanBuilder.build();
    }

    public void scanFrameworks() throws IOException {
        URL uri = Urls.toUrl(Urls.mergePaths(config().get(MESOS_URL), "master/state.json"));
        String data = Resources.toString(uri, Charsets.UTF_8);
        JsonArray frameworks = JsonFunctions.asJson().apply(data).getAsJsonObject().getAsJsonArray("frameworks");

        List<String> frameworkNames = MutableList.<String>of();
        for (int i = 0; i < frameworks.size(); i++) {
            JsonObject task = frameworks.get(i).getAsJsonObject();
            String id = task.get("id").getAsString();
            String pid = task.get("pid").getAsString();
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
                    .configure(MesosFramework.MESOS_CLUSTER, this);
            MesosFramework added = sensors().get(MESOS_FRAMEWORKS).addMemberChild(frameworkSpec);
            Entities.manage(added);
            added.start(ImmutableList.<Location>of());
        }
        sensors().set(MESOS_FRAMEWORK_LIST, frameworkNames);
    }

    public void disconnectSensors() {
        if (httpFeed != null && httpFeed.isRunning()) httpFeed.stop();
        if (frameworkScan != null && frameworkScan.isRunning()) frameworkScan.stop();
    }

    static {
        RendererHints.register(MESOS_FRAMEWORKS, RendererHints.namedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(MESOS_TASKS, RendererHints.namedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(MESOS_APPLICATIONS, RendererHints.namedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));

        RendererHints.register(LOAD_1MIN, RendererHints.displayValue(MathFunctions.percent(2)));
        RendererHints.register(LOAD_5MIN, RendererHints.displayValue(MathFunctions.percent(2)));
        RendererHints.register(LOAD_15MIN, RendererHints.displayValue(MathFunctions.percent(2)));

        RendererHints.register(MEMORY_FREE_BYTES, RendererHints.displayValue(ByteSizeStrings.metric()));
        RendererHints.register(MEMORY_TOTAL_BYTES, RendererHints.displayValue(ByteSizeStrings.metric()));
    }

}
