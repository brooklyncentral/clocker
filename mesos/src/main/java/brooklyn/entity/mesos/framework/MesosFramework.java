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

import java.util.List;
import java.util.Map;

import com.google.common.reflect.TypeToken;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.effector.MethodEffector;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.group.DynamicGroup;
import org.apache.brooklyn.entity.stock.BasicStartable;

import brooklyn.entity.mesos.MesosAttributes;
import brooklyn.entity.mesos.MesosCluster;
import brooklyn.entity.mesos.task.MesosTask;

/**
 * A Mesos framework.
 */
@ImplementedBy(MesosFrameworkImpl.class)
public interface MesosFramework extends BasicStartable {

    AttributeSensor<DynamicGroup> FRAMEWORK_TASKS = Sensors.newSensor(DynamicGroup.class, "mesos.framework.tasks", "Framework tasks");

    AttributeSensorAndConfigKey<Entity, Entity> MESOS_CLUSTER = MesosAttributes.MESOS_CLUSTER;

    AttributeSensor<Integer> MESOS_COMPLETED_TASKS = Sensors.newIntegerSensor("mesos.framework.tasks.completed", "Number of completed tasks");
    AttributeSensor<Integer> MESOS_RUNNING_TASKS = Sensors.newIntegerSensor("mesos.framework.tasks.running", "Number of running tasks");

    AttributeSensor<Void> MESOS_TASK_SCAN = Sensors.newSensor(Void.class, "mesos.framework.tasks.scan", "Notification of task scan");
    AttributeSensor<List<String>> MESOS_TASK_LIST = Sensors.newSensor(new TypeToken<List<String>>() { }, "mesos.framework.tasks.list", "List of Mesos tasks");

    // Configuration keys that identify the framework implementation and running instance.

    AttributeSensorAndConfigKey<String, String> FRAMEWORK_URL = ConfigKeys.newSensorAndConfigKey(String.class, "mesos.framework.url", "Mesos framework URL");
    AttributeSensorAndConfigKey<String, String> FRAMEWORK_ID = ConfigKeys.newSensorAndConfigKey(String.class, "mesos.framework.id", "Mesos framework ID");
    AttributeSensorAndConfigKey<String, String> FRAMEWORK_PID = ConfigKeys.newSensorAndConfigKey(String.class, "mesos.framework.pid", "Mesos framework PID");
    AttributeSensorAndConfigKey<String, String> FRAMEWORK_NAME = ConfigKeys.newSensorAndConfigKey(String.class, "mesos.framework.name", "Mesos framework name");

    /**
     * This will populate the framework-specific sensors, and should also
     * ensure that the {@link #SERVICE_UP} sensor is set appropriately.
     */
    void connectSensors();

    void disconnectSensors();

    // Effectors

    MethodEffector<Void> START_TASK = new MethodEffector<Void>(MesosFramework.class, "startTask");

    MesosTask startTask(Map<String, Object> taskFlags);

    // Methods
 
    List<Class<? extends Entity>> getSupported();

    MesosCluster getMesosCluster();

}
