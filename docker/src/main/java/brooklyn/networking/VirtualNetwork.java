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

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.BasicStartable;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.net.Cidr;

import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;

/**
 * A virtual network segment.
 */
@ImplementedBy(VirtualNetworkImpl.class)
public interface VirtualNetwork extends BasicStartable {

    @SetFromFlag("networkId")
    AttributeSensorAndConfigKey<String, String> NETWORK_ID = ConfigKeys.newStringSensorAndConfigKey("network.id", "ID of the network segment");

    @SetFromFlag("cidr")
    AttributeSensorAndConfigKey<Cidr, Cidr> NETWORK_CIDR = ConfigKeys.newSensorAndConfigKey(Cidr.class, "network.cidr", "CIDR for the network segment");

    @SetFromFlag("flags")
    ConfigKey<Map<String, Object>> NETWORK_PROVISIONING_FLAGS = ConfigKeys.newConfigKey(
            new TypeToken<Map<String, Object>>() { },
            "network.flags", "Extra configuration properties to set when provisioning the managed network segment",
            Maps.<String, Object>newHashMap());
 
    AttributeSensor<Integer> ALLOCATED_ADDRESSES = Sensors.newIntegerSensor("network.allocated", "Allocated IP addresses");

}
