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

import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.sensor.core.AttributeSensorAndConfigKey;
import org.apache.brooklyn.sensor.core.Sensors;
import org.apache.brooklyn.util.net.Cidr;

/**
 * IBM SDN VE configuration and attributes.
 */
public class SdnVeAttributes {

    public static final ConfigKey<InetAddress> GATEWAY = ConfigKeys.newConfigKey(InetAddress.class, "sdn.ibm.network.gateway", "Default gateway for the network segment");

    public static final ConfigKey<Boolean> ENABLE_PUBLIC_ACCESS = ConfigKeys.newBooleanConfigKey("sdn.ibm.public.enable", "Enable external routing for public access", Boolean.FALSE);

    public static final AttributeSensorAndConfigKey<Cidr, Cidr> PUBLIC_CIDR = ConfigKeys.newSensorAndConfigKey(Cidr.class, "sdn.ibm.public.cidr", "Externally routable CIDR");

    public static final AttributeSensor<InetAddress> PUBLIC_ADDRESS = Sensors.newSensor(InetAddress.class, "sdn.ibm.public.address", "Externally routable IP address");

}
