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
package clocker.docker.networking.entity.sdn;

import java.net.InetAddress;
import java.util.Map;

import clocker.docker.networking.entity.VirtualNetwork;
import clocker.docker.networking.location.NetworkProvisioningExtension;

import com.google.common.collect.Multimap;
import com.google.common.reflect.TypeToken;

import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.stock.BasicStartable;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.net.Cidr;

/**
 * An SDN provider implementation.
 */
public interface SdnProvider extends BasicStartable, NetworkProvisioningExtension {

    AttributeSensor<Cidr> APPLICATION_CIDR = Sensors.newSensor(Cidr.class, "sdn.application.cidr", "CIDR for application running in container");

    ConfigKey<Cidr> CONTAINER_NETWORK_CIDR = ConfigKeys.newConfigKey(Cidr.class, "sdn.network.cidr", "Pool CIDR for network allocation to containers", Cidr.LINK_LOCAL);
    ConfigKey<Integer> CONTAINER_NETWORK_SIZE = ConfigKeys.newIntegerConfigKey("sdn.network.size", "Size of network subnets as CIDR length (e.g. 24 for 254 hosts)", 24);

    AttributeSensor<Integer> ALLOCATED_NETWORKS = Sensors.newIntegerSensor("sdn.networks.allocated", "Number of allocated networks");

    AttributeSensor<Map<String, Cidr>> SUBNETS = Sensors.newSensor(
            new TypeToken<Map<String, Cidr>>() { }, "sdn.networks.addresses", "Map of network subnets that have been created");
    AttributeSensor<Map<String, VirtualNetwork>> SUBNET_ENTITIES = Sensors.newSensor(
            new TypeToken<Map<String, VirtualNetwork>>() { }, "sdn.networks.entities", "Map of managed network entities that have been created by this SDN");
    AttributeSensor<Map<String, Integer>> SUBNET_ADDRESS_ALLOCATIONS = Sensors.newSensor(
            new TypeToken<Map<String, Integer>>() { }, "sdn.networks.addresses.allocated", "Map of allocated address count on network subnets");

    AttributeSensor<Multimap<String, InetAddress>> CONTAINER_ADDRESSES = Sensors.newSensor(
            new TypeToken<Multimap<String, InetAddress>>() { }, "sdn.container.addresses", "Map of container ID to IP addresses on network");

    AttributeSensor<Group> SDN_NETWORKS = Sensors.newSensor(Group.class, "sdn.networks.managed", "Collection of virtual network entites managed by this SDN");
    AttributeSensor<Group> SDN_APPLICATIONS = Sensors.newSensor(Group.class, "sdn.networks.applications", "Groupings of application containers attached to each managed network");

    /* IP address management. */

    InetAddress getNextContainerAddress(String networkId);

    /* Access for network subnet CIDRs this SDN provder manages. */

    Cidr getNextSubnetCidr(String networkId);

    Cidr getNextSubnetCidr();

    void recordSubnetCidr(String networkId, Cidr subnetCidr);

    void recordSubnetCidr(String networkId, Cidr subnetCidr, int allocated);

    Cidr getSubnetCidr(String networkId);

    Object getNetworkMutex();

}
