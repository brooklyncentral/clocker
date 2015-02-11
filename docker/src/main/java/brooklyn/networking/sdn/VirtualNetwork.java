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
package brooklyn.networking.sdn;

import java.net.InetAddress;
import java.util.Collection;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.BasicStartable;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.net.Cidr;

import com.google.common.reflect.TypeToken;
import com.google.inject.ImplementedBy;

/**
 * A virtual network segment.
 */
@ImplementedBy(VirtualNetworkImpl.class)
public interface VirtualNetwork extends BasicStartable {

    @SetFromFlag("cidr")
    ConfigKey<Cidr> CIDR = ConfigKeys.newConfigKey(Cidr.class, "network.cidr", "CIDR for the network segment");
 
    @SetFromFlag("gateway")
    ConfigKey<InetAddress> GATEWAY = ConfigKeys.newConfigKey(InetAddress.class, "network.gateway", "Default gateway for the network segment");
 
    @SetFromFlag("excluded")
    ConfigKey<Collection<InetAddress>> EXCLUDED_ADDRESSES = ConfigKeys.newConfigKey(
            new TypeToken<Collection<InetAddress>>() { }, "network.addresses.excluded", "Collection of excluded IP addresses");
 
    @SetFromFlag("securityGroup")
    ConfigKey<String> SECURITY_GROUP = ConfigKeys.newStringConfigKey("network.securityGroup", "Security group to apply to the network");
 
    @SetFromFlag("firewall")
    ConfigKey<Boolean> ENABLE_FIREWALL = ConfigKeys.newBooleanConfigKey("network.firewall.enable", "Enable IP firewalling", Boolean.FALSE);
 
    @SetFromFlag("routing")
    ConfigKey<Boolean> ENABLE_ROUTING = ConfigKeys.newBooleanConfigKey("network.routing.enable", "Enable external routing", Boolean.FALSE);

    AttributeSensor<Integer> ALLOCATED_ADDRESSES = Sensors.newIntegerSensor("network.allocated", "Allocated IP addresses");

}
