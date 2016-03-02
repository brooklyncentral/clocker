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
package clocker.mesos.entity.task.marathon;

import java.util.List;
import java.util.Map;

import clocker.docker.entity.DockerHost;
import clocker.docker.entity.container.DockerContainer;
import clocker.docker.entity.util.DockerAttributes;
import clocker.mesos.entity.framework.marathon.MarathonFramework;
import clocker.mesos.entity.task.MesosTask;
import clocker.mesos.location.framework.marathon.MarathonTaskLocation;

import com.google.common.reflect.TypeToken;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.location.dynamic.LocationOwner;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.net.HasNetworkAddresses;

/**
 * A Marathon task.
 */
@ImplementedBy(MarathonTaskImpl.class)
public interface MarathonTask extends MesosTask, HasNetworkAddresses, LocationOwner<MarathonTaskLocation, MarathonTask>  {

    @SetFromFlag("command")
    ConfigKey<String> COMMAND = ConfigKeys.newStringConfigKey("marathon.task.command", "Marathon task command string");

    @SetFromFlag("args")
    ConfigKey<List<String>> ARGS = DockerContainer.DOCKER_IMAGE_ENTRYPOINT;

    @SetFromFlag("imageName")
    ConfigKey<String> DOCKER_IMAGE_NAME = DockerAttributes.DOCKER_IMAGE_NAME.getConfigKey();

    @SetFromFlag("imageVersion")
    ConfigKey<String> DOCKER_IMAGE_TAG = DockerAttributes.DOCKER_IMAGE_TAG.getConfigKey();

    @SetFromFlag("cpus")
    ConfigKey<Double> CPU_RESOURCES = ConfigKeys.newDoubleConfigKey("marathon.task.cpus", "Marathon task CPU resources (fractions of a core)");

    @SetFromFlag("memory")
    ConfigKey<Integer> MEMORY_RESOURCES = ConfigKeys.newIntegerConfigKey("marathon.task.mem", "Marathon task memory resources (number of MiB)");

    @SetFromFlag("openPorts")
    ConfigKey<List<Integer>> DOCKER_OPEN_PORTS = DockerAttributes.DOCKER_OPEN_PORTS;

    @SetFromFlag("directPorts")
    ConfigKey<List<Integer>> DOCKER_DIRECT_PORTS = DockerAttributes.DOCKER_DIRECT_PORTS;

    @SetFromFlag("portBindings")
    ConfigKey<Map<Integer, Integer>> DOCKER_PORT_BINDINGS = DockerAttributes.DOCKER_PORT_BINDINGS;

    @SetFromFlag("env")
    ConfigKey<Map<String, Object>> DOCKER_CONTAINER_ENVIRONMENT = DockerContainer.DOCKER_CONTAINER_ENVIRONMENT.getConfigKey();

    @SetFromFlag("volumes")
    ConfigKey<Map<String, String>> DOCKER_VOLUME_MAPPINGS = DockerHost.DOCKER_HOST_VOLUME_MAPPING.getConfigKey();

    @SetFromFlag("uris")
    AttributeSensorAndConfigKey<List<String>, List<String>> TASK_URI_LIST = ConfigKeys.newSensorAndConfigKey(
            new TypeToken<List<String>>() { },
            "marathon.task.uris", "List of URIs to copy to the Marathon task");

    @SetFromFlag("entity")
    AttributeSensorAndConfigKey<Entity, Entity> ENTITY = ConfigKeys.newSensorAndConfigKey(Entity.class,
            "marathon.task.entity", "The entity running in this Marathon task");

    @SetFromFlag("useSsh")
    ConfigKey<Boolean> DOCKER_USE_SSH = ConfigKeys.newConfigKeyWithDefault(DockerAttributes.DOCKER_USE_SSH, false);

    // Attributes

    AttributeSensor<String> APPLICATION_ID = Sensors.newStringSensor("marathon.task.appId", "Marathon task application id");
    AttributeSensor<Long> TASK_STARTED_AT = Sensors.newLongSensor("marathon.task.startedAt", "Time task started");
    AttributeSensor<Long> TASK_STAGED_AT = Sensors.newLongSensor("marathon.task.stagedAt", "Time task was staged");

    // Methods

    MarathonFramework getMarathonFramework();

    Entity getRunningEntity();

    void setRunningEntity(Entity entity);

}
