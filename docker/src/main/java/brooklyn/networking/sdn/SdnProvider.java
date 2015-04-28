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
import java.util.Map;

import org.jclouds.net.domain.IpPermission;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.BasicStartable;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.container.DockerAttributes;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.networking.VirtualNetwork;
import brooklyn.networking.location.NetworkProvisioningExtension;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.net.Cidr;

import com.google.common.collect.Multimap;
import com.google.common.reflect.TypeToken;
import com.google.inject.ImplementedBy;

/**
 * An SDN provider implementation.
 */
@ImplementedBy(SdnProviderImpl.class)
public interface SdnProvider extends BasicStartable, NetworkProvisioningExtension {

    ConfigKey<Cidr> AGENT_CIDR = ConfigKeys.newConfigKey(Cidr.class, "sdn.agent.cidr", "CIDR for agent address allocation");
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

    AttributeSensor<Group> SDN_AGENTS = Sensors.newSensor(Group.class, "sdn.agents", "Group of SDN agent services");

    AttributeSensor<Integer> ALLOCATED_IPS = Sensors.newIntegerSensor("sdn.agent.ips", "Number of allocated IPs for agents");
    AttributeSensor<Map<String, InetAddress>> ALLOCATED_ADDRESSES = Sensors.newSensor(
            new TypeToken<Map<String, InetAddress>>() { }, "sdn.agent.addresses", "Allocated IP addresses for agents");

    @SetFromFlag("agentSpec")
    AttributeSensorAndConfigKey<EntitySpec<?>,EntitySpec<?>> SDN_AGENT_SPEC = ConfigKeys.newSensorAndConfigKey(
            new TypeToken<EntitySpec<?>>() { }, "sdn.agent.spec", "SDN agent specification");

    @SetFromFlag("dockerInfrastructure")
    AttributeSensorAndConfigKey<Entity, Entity> DOCKER_INFRASTRUCTURE = DockerAttributes.DOCKER_INFRASTRUCTURE;

    AttributeSensor<Group> SDN_NETWORKS = Sensors.newSensor(Group.class, "sdn.networks.managed", "Collection of virtual network entites managed by this SDN");
    AttributeSensor<Group> SDN_APPLICATIONS = Sensors.newSensor(Group.class, "sdn.networks.applications", "Groupings of application containers attached to each managed network");

    Collection<IpPermission> getIpPermissions(String source);

    DynamicCluster getDockerHostCluster();

    Group getAgents();

    /* IP address management. */

    InetAddress getNextContainerAddress(String networkId);

    InetAddress getNextAgentAddress(String agentId);

    /* Access for network subnet CIDRs this SDN provder manages. */

    Cidr getNextSubnetCidr(String networkId);

    Cidr getNextSubnetCidr();

    void recordSubnetCidr(String networkId, Cidr subnetCidr);

    void recordSubnetCidr(String networkId, Cidr subnetCidr, int allocated);

    Cidr getSubnetCidr(String networkId);

    /* Callbacks for hosts using this SDN provider. */

    void addHost(DockerHost host);

    void removeHost(DockerHost host);

    Object getNetworkMutex();

}
