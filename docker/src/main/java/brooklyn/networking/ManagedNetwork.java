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
package brooklyn.networking;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.BasicStartable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.networking.location.NetworkProvisioningExtension;

/**
 * The actual network segment that is provisioned when a {@link Virtualnetwork} is instantiated.
 * <p>
 * Different {@link NetworkProvisioningExtension} implementations will create their own subclasses
 * of this entity.
 */
public interface ManagedNetwork extends BasicStartable {

    AttributeSensor<Entity> VIRTUAL_NETWORK = Sensors.newSensor(Entity.class, "network.entity.virtual", "Virtual network Entity");

}
