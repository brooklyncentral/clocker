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

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Resources;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityFunctions;
import org.apache.brooklyn.core.feed.ConfigToAttributes;
import org.apache.brooklyn.entity.group.DynamicGroup;
import org.apache.brooklyn.entity.stock.BasicStartableImpl;
import org.apache.brooklyn.entity.stock.DelegateEntity;
import org.apache.brooklyn.feed.function.FunctionFeed;
import org.apache.brooklyn.feed.function.FunctionPollConfig;
import org.apache.brooklyn.feed.http.JsonFunctions;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.time.Duration;

import brooklyn.entity.mesos.MesosCluster;
import brooklyn.entity.mesos.task.MesosTask;
import brooklyn.entity.mesos.task.marathon.MarathonTask;

/**
 * Mesos frameworks shared implementation.
 */
public class MesosFrameworkImpl extends BasicStartableImpl implements MesosFramework {

    private static final Logger LOG = LoggerFactory.getLogger(MesosFramework.class);

    private transient FunctionFeed taskScan;

    @Override
    public void init() {
        LOG.info("Connecting to framework id: {}", getId());
        super.init();

        ConfigToAttributes.apply(this);

        DynamicGroup tasks = addChild(EntitySpec.create(DynamicGroup.class)
                .configure(DynamicGroup.MEMBER_DELEGATE_CHILDREN, true)
                .displayName("Framework Tasks"));
        if (Entities.isManaged(this)) Entities.manage(tasks);
        sensors().set(FRAMEWORK_TASKS, tasks);

        sensors().set(Attributes.MAIN_URI, URI.create(config().get(FRAMEWORK_URL)));
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        sensors().set(SERVICE_UP, Boolean.FALSE);

        super.start(locations);

        connectSensors();
    }

    @Override
    public void stop() {
        disconnectSensors();

        // TODO stop all tasks belonging to this framework
        // TODO should we check if we own the task (i.e. started it) first?

        sensors().set(SERVICE_UP, Boolean.FALSE);
    }

    @Override
    public void connectSensors() {
        Duration scanInterval = config().get(MesosCluster.SCAN_INTERVAL);
        FunctionFeed.Builder taskScanBuilder = FunctionFeed.builder()
                .entity(this)
                .poll(new FunctionPollConfig<Object, Void>(MESOS_TASK_SCAN)
                        .period(scanInterval)
                        .description("Scan Tasks")
                        .callable(new Callable<Void>() {
                                @Override
                                public Void call() throws Exception {
                                    scanTasks();
                                    return null;
                                }
                            })
                        .onFailureOrException(Functions.<Void>constant(null)));
        taskScan = taskScanBuilder.build();
    }

    public void scanTasks() throws IOException {
        String frameworkId = sensors().get(FRAMEWORK_ID);
        Entity mesosCluster = sensors().get(MESOS_CLUSTER);
        URL uri = Urls.toUrl(Urls.mergePaths(config().get(MesosCluster.MESOS_URL), "master/state.json"));
        String data = Resources.toString(uri, Charsets.UTF_8);
        JsonArray frameworks = JsonFunctions.asJson().apply(data).getAsJsonObject().getAsJsonArray("frameworks");
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
                                .configure(MesosTask.MESOS_CLUSTER, mesosCluster)
                                .configure(MesosTask.TASK_NAME, name)
                                .configure(MesosTask.FRAMEWORK, this);
                        task = sensors().get(FRAMEWORK_TASKS).addMemberChild(taskSpec);
                        Entities.manage(task);
                        task.start(ImmutableList.<Location>of());
                    }
                    if (task != null) {
                        taskNames.add(name);
                        task.sensors().set(MesosTask.TASK_ID, id);
                        task.sensors().set(MesosTask.TASK_STATE, state);
                    }
                }
                sensors().set(MESOS_TASK_LIST, taskNames);
                return;
            }
        }
    }

    @Override
    public void disconnectSensors() {
        if (taskScan  != null && taskScan.isRunning()) taskScan.stop();
    }

    @Override
    public MarathonTask startTask(Map<String, Object> taskFlags) {
        throw new UnsupportedOperationException("Not implemented on this framework");
    }

    static {
        RendererHints.register(MESOS_CLUSTER, RendererHints.namedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(FRAMEWORK_TASKS, RendererHints.namedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
    }

}
