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
package clocker.docker.networking.entity;

import java.util.Map;

import clocker.docker.networking.entity.sdn.util.SdnAttributes;
import clocker.docker.networking.location.NetworkProvisioningExtension;

import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.stock.BasicStartable;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.net.Cidr;

/**
 * A virtual network segment.
 */
@ImplementedBy(VirtualNetworkImpl.class)
public interface VirtualNetwork extends BasicStartable {

    @SetFromFlag("networkId")
    AttributeSensorAndConfigKey<String, String> NETWORK_ID = ConfigKeys.newStringSensorAndConfigKey("network.id", "ID of the network segment");

    @SetFromFlag("cidr")
    AttributeSensorAndConfigKey<Cidr, Cidr> NETWORK_CIDR = ConfigKeys.newSensorAndConfigKey(Cidr.class, "network.cidr", "CIDR for the network segment");

    @SetFromFlag("sdn")
    AttributeSensorAndConfigKey<Entity, Entity> SDN_PROVIDER = SdnAttributes.SDN_PROVIDER;

    @SetFromFlag("flags")
    ConfigKey<Map<String, Object>> NETWORK_PROVISIONING_FLAGS = ConfigKeys.newConfigKey(
            new TypeToken<Map<String, Object>>() { },
            "network.flags", "Extra configuration properties to set when provisioning the managed network segment",
            Maps.<String, Object>newHashMap());
 
    AttributeSensor<Integer> ALLOCATED_ADDRESSES = Sensors.newIntegerSensor("network.allocated", "Allocated IP addresses");

    AttributeSensor<NetworkProvisioningExtension> NETWORK_PROVISIONER = Sensors.newSensor(NetworkProvisioningExtension.class, "network.provsioner", "Location extension for provisioning networks");

}
