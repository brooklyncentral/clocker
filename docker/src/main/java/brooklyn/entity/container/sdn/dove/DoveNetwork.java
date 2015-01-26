/*
 * Copyright 2014 by Cloudsoft Corporation Limited
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
package brooklyn.entity.container.sdn.dove;

import java.net.InetAddress;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.container.sdn.SdnAgent;
import brooklyn.entity.container.sdn.SdnProvider;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.net.Cidr;

/**
 * A collection of machines that are part of the Dove SDN VE network.
 */
@Catalog(name = "Dove SDN", description = "Dove SDN VE Provider.")
@ImplementedBy(DoveNetworkImpl.class)
public interface DoveNetwork extends SdnProvider {

    @SetFromFlag("cidr")
    ConfigKey<Cidr> CIDR = SdnAgent.CIDR;

    @SetFromFlag("dmc")
    ConfigKey<InetAddress> DOVE_CONTROLLER = ConfigKeys.newConfigKey(InetAddress.class, "sdn.dove.dmc.address", "The Dove DMC IP address");

    @SetFromFlag("vlanId")
    ConfigKey<Integer> VLAN_ID = ConfigKeys.newIntegerConfigKey("sdn.dove.vlanId", "Dove Softlayer VLAN ID");

    AttributeSensorAndConfigKey<String, String> CONFIGURATION_XML_TEMPLATE = ConfigKeys.newStringSensorAndConfigKey("sdn.dove.config.xml.url",
            "Configuration XML template for Dove SDN", "classpath://brooklyn/entity/container/sdn/dove/dove.xml");

    AttributeSensorAndConfigKey<String, String> NETWORK_SETUP_SCRIPT_URL = ConfigKeys.newStringSensorAndConfigKey("sdn.dove.networkSetupScript.url",
            "Network setup script file for Dove SDN", "classpath://brooklyn/entity/container/sdn/dove/setup_networkv2.sh");


}
