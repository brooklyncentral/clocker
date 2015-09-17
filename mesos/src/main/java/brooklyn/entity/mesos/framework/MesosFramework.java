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

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.stock.BasicStartable;

/**
 * A Mesos framework.
 */
@ImplementedBy(MesosFrameworkImpl.class)
public interface MesosFramework extends BasicStartable {

    AttributeSensorAndConfigKey<String, String> FRAMEWORK_URL = ConfigKeys.newSensorAndConfigKey(String.class, "framework.url", "Mesos framework URL");

    AttributeSensorAndConfigKey<Entity, Entity> MESOS_CLUSTER = ConfigKeys.newSensorAndConfigKey(Entity.class, "mesos.cluster", "Mesos cluster entity");

    AttributeSensor<String> FRAMEWORK_ID = Sensors.newStringSensor("mesos.framework.id", "Mesos framework ID");

}
