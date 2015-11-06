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
package brooklyn.entity.mesos.framework.marathon;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationDefinition;
import org.apache.brooklyn.api.mgmt.LocationManager;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.location.BasicLocationDefinition;
import org.apache.brooklyn.core.location.dynamic.LocationOwner;
import org.apache.brooklyn.entity.group.Cluster;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.feed.http.HttpFeed;
import org.apache.brooklyn.feed.http.HttpPollConfig;
import org.apache.brooklyn.feed.http.HttpValueFunctions;
import org.apache.brooklyn.feed.http.JsonFunctions;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.QuorumCheck.QuorumChecks;
import org.apache.brooklyn.util.guava.Functionals;
import org.apache.brooklyn.util.net.Urls;

import brooklyn.entity.container.DockerUtils;
import brooklyn.entity.container.docker.application.VanillaDockerApplication;
import brooklyn.entity.mesos.MesosUtils;
import brooklyn.entity.mesos.framework.MesosFrameworkImpl;
import brooklyn.entity.mesos.task.marathon.MarathonTask;
import brooklyn.location.mesos.framework.marathon.MarathonLocation;
import brooklyn.location.mesos.framework.marathon.MarathonResolver;

/**
 * The Marathon framework implementation.
 */
public class MarathonFrameworkImpl extends MesosFrameworkImpl implements MarathonFramework {

    private static final Logger LOG = LoggerFactory.getLogger(MarathonFramework.class);

    private transient HttpFeed httpFeed;

    @Override
    public void init() {
        super.init();

        DynamicCluster tasks = addChild(EntitySpec.create(DynamicCluster.class)
                .configure(Cluster.INITIAL_SIZE, 0)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(MarathonTask.class))
                .configure(DynamicCluster.QUARANTINE_FAILED_ENTITIES, true)
                .configure(DynamicCluster.RUNNING_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty())
                .configure(DynamicCluster.UP_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty())
                .displayName("Marathon Tasks"));

        if (Entities.isManaged(this)) {
            Entities.manage(tasks);
        }

        sensors().set(MARATHON_TASK_CLUSTER, tasks);
    }

    @Override
    public String getIconUrl() { return "classpath://marathon-logo.png"; }

    @Override
    public DynamicCluster getTaskCluster() {
        return sensors().get(MARATHON_TASK_CLUSTER);
    }

    @Override
    public void connectSensors() {
        super.connectSensors();

        HttpFeed.Builder httpFeedBuilder = HttpFeed.builder()
                .entity(this)
                .period(500, TimeUnit.MILLISECONDS)
                .baseUri(sensors().get(FRAMEWORK_URL))
                .poll(new HttpPollConfig<List<String>>(MARATHON_APPLICATIONS)
                        .suburl("/v2/apps/")
                        .onSuccess(Functionals.chain(HttpValueFunctions.jsonContents(), JsonFunctions.walk("apps"), JsonFunctions.forEach(JsonFunctions.<String>getPath("id"))))
                        .onFailureOrException(Functions.constant(Arrays.asList(new String[0]))))
                .poll(new HttpPollConfig<String>(MARATHON_VERSION)
                        .suburl("/v2/info/")
                        .onSuccess(HttpValueFunctions.jsonContents("version", String.class))
                        .onFailureOrException(Functions.constant("")))
                .poll(new HttpPollConfig<Boolean>(SERVICE_UP)
                        .suburl("/ping")
                        .onSuccess(HttpValueFunctions.responseCodeEquals(200))
                        .onFailureOrException(Functions.constant(Boolean.FALSE)));
        httpFeed = httpFeedBuilder.build();
    }

    @Override
    public void disconnectSensors() {
        if (httpFeed != null && httpFeed.isRunning()) httpFeed.stop();
        super.disconnectSensors();
    }

    @Override
    public String startApplication(String id, Map<String, Object> flags) {
        Map<String, Object> substitutions = MutableMap.copyOf(flags);
        substitutions.put("id", id);

        Optional<String> result = MesosUtils.httpPost(Urls.mergePaths(sensors().get(FRAMEWORK_URL), "v2/apps"), "classpath:///brooklyn/entity/mesos/framework/marathon/create-app.json", substitutions);
        if (!result.isPresent()) {
            throw new IllegalStateException("Failed to start Marathon task");
        } else {
            LOG.debug("Success creating Marathon task");
            JsonElement json = JsonFunctions.asJson().apply(result.get());
            String version = json.getAsJsonObject().get("version").getAsString();
            return version;
        }
    }

    @Override
    public String stopApplication(String id) {
        Optional<String> result = MesosUtils.httpDelete(Urls.mergePaths(sensors().get(FRAMEWORK_URL), "v2/apps", id));
        if (!result.isPresent()) {
            throw new IllegalStateException("Failed to stop Marathon task");
        } else {
            LOG.debug("Success deleting Marathon task");
            JsonElement json = JsonFunctions.asJson().apply(result.get());
            String deployment = json.getAsJsonObject().get("deploymentId").getAsString();
            return deployment;
        }
    }
 
    @Override
    public void start(Collection<? extends Location> locations) {
        // Setup port forwarding
        MarathonPortForwarder portForwarder = new MarathonPortForwarder();
        portForwarder.injectManagementContext(getManagementContext());
        portForwarder.init(URI.create(sensors().get(FRAMEWORK_URL)));

        // Create Marathon location
        Map<String, ?> flags = MutableMap.<String, Object>builder()
                .put("portForwarder", portForwarder)
                .putAll(config().get(LOCATION_FLAGS))
                .build();
        MarathonLocation marathon = createLocation(flags);

        // Setup subnet tier
//        SubnetTier subnetTier = addChild(EntitySpec.create(SubnetTier.class, SubnetTierImpl.class)
//                .configure(SubnetTier.PORT_FORWARDER, portForwarder)
//                .configure(SubnetTier.SUBNET_CIDR, Cidr.UNIVERSAL));
//        Entities.manage(subnetTier);
//        subnetTier.start(ImmutableList.of(marathon));
//        sensors().set(MARATHON_SUBNET_TIER, subnetTier);

        // Add task cluster
        DynamicCluster tasks = getTaskCluster();
        Entities.start(tasks, ImmutableList.of(marathon));
        tasks.sensors().set(SERVICE_UP, Boolean.TRUE);

        super.start(locations);
    }

    /**
     * Create a new {@link MarathonLocation} for this framework.
     */
    @Override
    public MarathonLocation createLocation(Map<String, ?> flags) {
        String locationName = MarathonResolver.MARATHON + "-" + getId();
        String locationSpec = String.format(MarathonResolver.MARATHON_FRAMEWORK_SPEC, getId());
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

        LOG.info("New Marathon framework location {} created", location);
        return (MarathonLocation) location;
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
        MarathonLocation location = getDynamicLocation();

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
    public void stop() {
        super.stop();

        // Stop all of our managed Marathon tasks
        Collection<Entity> tasks = getTaskCluster().getMembers();
        for (Entity task : tasks) {
            ((MarathonTask) task).stop();
        }

        deleteLocation();
    }

    @Override
    public MarathonLocation getDynamicLocation() {
        return (MarathonLocation) sensors().get(DYNAMIC_LOCATION);
    }

    @Override
    public boolean isLocationAvailable() {
        return true;
    }
 
    @Override
    public List<Class<? extends Entity>> getSupported() {
        return ImmutableList.<Class<? extends Entity>>builder()
                .addAll(super.getSupported())
                .add(VanillaDockerApplication.class)
                .add(SoftwareProcess.class)
                .build();
    }

}
