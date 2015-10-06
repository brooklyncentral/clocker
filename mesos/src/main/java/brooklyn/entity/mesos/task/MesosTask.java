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
package brooklyn.entity.mesos.task;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.objs.HasShortName;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.stock.BasicStartable;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import brooklyn.entity.mesos.MesosAttributes;

/**
 * A Mesos task.
 */
@ImplementedBy(MesosTaskImpl.class)
public interface MesosTask extends BasicStartable, HasShortName {

    @SetFromFlag("name")
    AttributeSensorAndConfigKey<String, String> TASK_NAME = ConfigKeys.newSensorAndConfigKey(String.class, "mesos.task.name", "Mesos task name");

    @SetFromFlag("framework")
    AttributeSensorAndConfigKey<Entity, Entity> FRAMEWORK = ConfigKeys.newSensorAndConfigKey(Entity.class, "mesos.task.framework", "Mesos task framework");

    @SetFromFlag("cluster")
    AttributeSensorAndConfigKey<Entity, Entity> MESOS_CLUSTER = MesosAttributes.MESOS_CLUSTER;

    AttributeSensor<String> TASK_ID = Sensors.newStringSensor("mesos.task.id", "Mesos task ID");
    AttributeSensor<String> TASK_STATE = Sensors.newStringSensor("mesos.task.state", "Mesos task state");
    AttributeSensor<String> FRAMEWORK_ID = Sensors.newStringSensor("mesos.task.framework.id", "Mesos task framework ID");
    AttributeSensor<Boolean> MANAGED = Sensors.newBooleanSensor("mesos.task.managed", "Task is managed by Clocker");

    enum TaskState { TASK_FINISHED, TASK_RUNNING, TASK_FAILED, TASK_ERROR, TASK_KILLED, TASK_LOST, TASK_STAGING, TASK_STARTING }
}
