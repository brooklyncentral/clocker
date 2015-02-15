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
package brooklyn.networking.sdn.ibm;

import java.net.InetAddress;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;

/**
 * IBM SDN VE configuration and attributes.
 */
public class SdnVeAttributes {

    public static final ConfigKey<InetAddress> GATEWAY = ConfigKeys.newConfigKey(InetAddress.class, "sdn.ibm.network.gateway", "Default gateway for the network segment");

    public static final ConfigKey<Boolean> ENABLE_ROUTING = ConfigKeys.newBooleanConfigKey("sdn.ibm.network.routing.enable", "Enable external routing", Boolean.FALSE);

}
