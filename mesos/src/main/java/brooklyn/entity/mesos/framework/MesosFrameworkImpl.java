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
package brooklyn.entity.mesos.framework;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityFunctions;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.feed.ConfigToAttributes;
import org.apache.brooklyn.core.location.Locations;
import org.apache.brooklyn.entity.group.Cluster;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.entity.stock.BasicStartableImpl;
import org.apache.brooklyn.entity.stock.DelegateEntity;
import org.apache.brooklyn.feed.http.HttpFeed;
import org.apache.brooklyn.feed.http.HttpPollConfig;
import org.apache.brooklyn.feed.http.HttpValueFunctions;
import org.apache.brooklyn.feed.http.JsonFunctions;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.QuorumCheck.QuorumChecks;
import org.apache.brooklyn.util.guava.Functionals;
import org.apache.brooklyn.util.time.Duration;

import brooklyn.entity.mesos.MesosCluster;
import brooklyn.entity.mesos.task.MesosTask;

/**
 * Mesos frameworks shared implementation.
 */
public class MesosFrameworkImpl extends BasicStartableImpl implements MesosFramework {

    private static final Logger LOG = LoggerFactory.getLogger(MesosFramework.class);

    private transient HttpFeed taskScan;

    @Override
    public void init() {
        LOG.info("Connecting to framework id: {}", getId());
        super.init();

        ConfigToAttributes.apply(this);

        DynamicCluster tasks = addChild(EntitySpec.create(DynamicCluster.class)
                .configure(Cluster.INITIAL_SIZE, 0)
                .configure(DynamicCluster.MEMBER_SPEC, config().get(FRAMEWORK_TASK_SPEC))
                .configure(DynamicCluster.QUARANTINE_FAILED_ENTITIES, true)
                .configure(DynamicCluster.RUNNING_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty())
                .configure(DynamicCluster.UP_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty())
                .displayName("Framework Tasks"));
        if (Entities.isManaged(this)) Entities.manage(tasks);
        sensors().set(FRAMEWORK_TASKS, tasks);

        sensors().set(Attributes.MAIN_URI, URI.create(config().get(FRAMEWORK_URL)));
    }

    @Override
    public void start(Collection<? extends Location> locs) {
        addLocations(locs);
        List<Location> locations = MutableList.copyOf(Locations.getLocationsCheckingAncestors(locs, this));

        sensors().set(SERVICE_UP, Boolean.FALSE);

        connectSensors();

        super.start(locations);
    }

    @Override
    public void stop() {
        disconnectSensors();

        sensors().set(SERVICE_UP, Boolean.FALSE);
    }

    @Override
    public void connectSensors() {
        // Start task cluster
        DynamicCluster tasks = getTaskCluster();
        Entities.start(tasks, ImmutableList.of(getMesosCluster().getDynamicLocation()));
        ServiceStateLogic.setExpectedState(tasks, Lifecycle.RUNNING);
        tasks.sensors().set(SERVICE_UP, Boolean.TRUE);

        Duration scanInterval = config().get(MesosCluster.SCAN_INTERVAL);
        HttpFeed.Builder taskScanBuilder = HttpFeed.builder()
                .entity(this)
                .period(scanInterval)
                .baseUri(config().get(MesosCluster.MESOS_URL))
                .credentialsIfNotNull(config().get(MesosCluster.MESOS_USERNAME), config().get(MesosCluster.MESOS_PASSWORD))
                .poll(HttpPollConfig.forSensor(MESOS_TASK_LIST)
                        .description("Scan Tasks")
                        .suburl("/master/state.json")
                        .onSuccess(Functionals.chain(HttpValueFunctions.jsonContents(), JsonFunctions.walk("frameworks"), new Function<JsonElement, List<String>>() {
                            @Override
                            public List<String> apply(JsonElement frameworks) {
                                return scanTasks(frameworks.getAsJsonArray());
                            }
                        }))
                        .onFailureOrException(Functions.<List<String>>constant(null)));
        taskScan = taskScanBuilder.build();
    }

    public List<String> scanTasks(JsonArray frameworks) {
        String frameworkId = sensors().get(FRAMEWORK_ID);
        Entity mesosCluster = sensors().get(MESOS_CLUSTER);
        for (int i = 0; i < frameworks.size(); i++) {
            JsonObject framework = frameworks.get(i).getAsJsonObject();
            if (frameworkId.equals(framework.get("id").getAsString())) {
                JsonArray completed = framework.getAsJsonArray("completed_tasks");
                JsonArray tasks = framework.getAsJsonArray("tasks");
                sensors().set(MESOS_COMPLETED_TASKS, completed.size());
                sensors().set(MESOS_RUNNING_TASKS, tasks.size());

                List<String> taskNames = MutableList.<String>of();
                for (int j = 0; j < tasks.size(); j++) {
                    JsonObject json = tasks.get(j).getAsJsonObject();
                    String id = json.get("id").getAsString();
                    String name = json.get("name").getAsString();
                    String state = json.get("state").getAsString();

                    Optional<Entity> entity = Iterables.tryFind(sensors().get(FRAMEWORK_TASKS).getMembers(),
                              Predicates.compose(Predicates.equalTo(name), EntityFunctions.attribute(MesosTask.TASK_NAME)));
                    MesosTask task = null;
                    if (entity.isPresent()) {
                        task = (MesosTask) entity.get();
                    } else if (state.equals(MesosTask.TaskState.TASK_RUNNING.name())) {
                        EntitySpec<MesosTask> taskSpec = EntitySpec.create(MesosTask.class)
                                .configure(MesosTask.MANAGED, Boolean.FALSE)
                                .configure(MesosTask.MESOS_CLUSTER, mesosCluster)
                                .configure(MesosTask.TASK_NAME, name)
                                .configure(MesosTask.FRAMEWORK, this);

                        task = getTaskCluster().addMemberChild(taskSpec);
                        Entities.manage(task);
                        task.start(ImmutableList.<Location>of());
                    }
                    if (task != null) {
                        taskNames.add(name);
                        task.sensors().set(MesosTask.TASK_ID, id);
                        task.sensors().set(MesosTask.TASK_STATE, state);
                    }
                }
                for (Entity member : ImmutableList.copyOf(getTaskCluster().getMembers())) {
                    final String name = member.sensors().get(MesosTask.TASK_NAME);
                    if (name != null) {
                        Optional<String> found = Iterables.tryFind(taskNames, Predicates.equalTo(name));
                        if (found.isPresent()) continue;
                    }

                    // Stop and then remove the task as it is no longer running, unless ON_FIRE
                    Lifecycle state = member.sensors().get(Attributes.SERVICE_STATE_ACTUAL);
                    if (Lifecycle.ON_FIRE.equals(state) || Lifecycle.STARTING.equals(state)) {
                        continue;
                    } else if (Lifecycle.STOPPING.equals(state) || Lifecycle.STOPPED.equals(state)) {
                        getTaskCluster().removeMember(member);
                        getTaskCluster().removeChild(member);
                        Entities.unmanage(member);
                    } else {
                        ServiceStateLogic.setExpectedState(member, Lifecycle.STOPPING);
                    }
                }
                return taskNames;
            }
        }
        // not found
        return null;
    }

    @Override
    public void disconnectSensors() {
        if (taskScan  != null && taskScan.isActivated()) taskScan.destroy();
    }

    @Override
    public MesosTask startTask(Map<String, Object> taskFlags) {
        throw new UnsupportedOperationException("Not implemented on this framework");
    }

    @Override
    public List<Class<? extends Entity>> getSupported() {
        return ImmutableList.of();
    }

    @Override
    public MesosCluster getMesosCluster() {
        return (MesosCluster) sensors().get(MESOS_CLUSTER);
    }

    @Override
    public DynamicCluster getTaskCluster() {
        return sensors().get(FRAMEWORK_TASKS);
    }

    static {
        RendererHints.register(FRAMEWORK_URL, RendererHints.namedActionWithUrl());
        RendererHints.register(MESOS_CLUSTER, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(FRAMEWORK_TASKS, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
    }

}
