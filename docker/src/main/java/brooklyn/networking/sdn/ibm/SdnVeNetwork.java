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
package brooklyn.networking.sdn.ibm;

import java.net.InetAddress;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.networking.sdn.SdnProvider;
import brooklyn.util.flags.SetFromFlag;

/**
 * A collection of machines that are part of the IBM SDN VE network.
 */
@Catalog(name = "IBM SDN VE", description = "IBM SDN VE Network Provider")
@ImplementedBy(SdnVeNetworkImpl.class)
public interface SdnVeNetwork extends SdnProvider {

    @SetFromFlag("dmc")
    ConfigKey<InetAddress> DOVE_CONTROLLER = ConfigKeys.newConfigKey(InetAddress.class, "sdn.ibm.dmc.address", "The IBM SDN VE DMC IP address");

    @SetFromFlag("vlanId")
    ConfigKey<Integer> VLAN_ID = ConfigKeys.newIntegerConfigKey("sdn.ibm.vlanId", "Softlayer VLAN ID");

    AttributeSensorAndConfigKey<String, String> CONFIGURATION_XML_TEMPLATE = ConfigKeys.newStringSensorAndConfigKey("sdn.ibm.config.xml.url",
            "Configuration XML template for Dove SDN", "classpath://brooklyn/networking/sdn/ibm/dove.xml");

    AttributeSensorAndConfigKey<String, String> NETWORK_SETUP_SCRIPT_URL = ConfigKeys.newStringSensorAndConfigKey("sdn.ibm.networkSetup.script.url",
            "Network setup script file for Dove SDN", "classpath://brooklyn/networking/sdn/ibm/setup_networkv2.sh");


}
