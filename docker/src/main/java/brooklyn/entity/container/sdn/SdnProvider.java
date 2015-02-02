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
package brooklyn.entity.container.sdn;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Arrays;
import java.util.Map;

import org.jclouds.net.domain.IpPermission;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.BasicStartable;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.container.DockerAttributes;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.net.Cidr;

import com.google.common.reflect.TypeToken;

/**
 * An SDN provider implementation.
 */
public interface SdnProvider extends BasicStartable {

    @SetFromFlag("cidr")
    ConfigKey<Cidr> CIDR = ConfigKeys.newConfigKey(Cidr.class, "sdn.cidr", "CIDR for address allocation");

    @SetFromFlag("containerCidr")
    ConfigKey<Cidr> CONTAINER_CIDR = ConfigKeys.newConfigKey(Cidr.class, "sdn.container.cidr", "CIDR for address allocation to containers");

    ConfigKey<Map<String, Cidr>> NETWORK_SPEC = ConfigKeys.newConfigKey(
            new TypeToken<Map<String, Cidr>>() { }, "sdn.network.spec", "Map of network subnets to be created",
            MutableMap.<String, Cidr>of("clocker", new Cidr("50.0.0.0/24")));

    ConfigKey<Collection<String>> NETWORKS = ConfigKeys.newConfigKey(
            new TypeToken<Collection<String>>() { }, "sdn.networks", "Collection of networks to be used",
            Arrays.asList("clocker"));

    AttributeSensor<Group> SDN_AGENTS = Sensors.newSensor(Group.class, "sdn.agents", "Group of SDN agent services");

    AttributeSensor<Integer> ALLOCATED_IPS = Sensors.newIntegerSensor("sdn.agent.ips", "Number of allocated IPs for agents");
    AttributeSensor<Map<String, InetAddress>> ALLOCATED_ADDRESSES = Sensors.newSensor(
            new TypeToken<Map<String, InetAddress>>() { }, "sdn.agent.addresses", "Allocated IP addresses for agents");

    AttributeSensor<Integer> ALLOCATED_CONTAINER_IPS = Sensors.newIntegerSensor("sdn.container.ips", "Number of allocated IPs for containers");
    AttributeSensor<Map<String, InetAddress>> ALLOCATED_CONTAINER_ADDRESSES = Sensors.newSensor(
            new TypeToken<Map<String, InetAddress>>() { }, "sdn.container.addresses", "Allocated IP addresses for containers");

    @SetFromFlag("agentSpec")
    AttributeSensorAndConfigKey<EntitySpec<?>,EntitySpec<?>> SDN_AGENT_SPEC = ConfigKeys.newSensorAndConfigKey(
            new TypeToken<EntitySpec<?>>() { }, "sdn.agent.spec", "SDN agent specification");

    @SetFromFlag("dockerInfrastructure")
    AttributeSensorAndConfigKey<Entity, Entity> DOCKER_INFRASTRUCTURE = DockerAttributes.DOCKER_INFRASTRUCTURE;

    Collection<IpPermission> getIpPermissions();

    DynamicCluster getDockerHostCluster();

    Group getAgents();

    InetAddress getNextContainerAddress();

    InetAddress getNextAddress();

    Map<String, InetAddress> getContainerAddresses();

    Map<String, InetAddress> getAgentAddresses();

    void addHost(Entity host);

    void removeHost(Entity host);
}
