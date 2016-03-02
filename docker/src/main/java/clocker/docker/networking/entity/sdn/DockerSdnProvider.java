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
import java.util.Collection;
import java.util.Map;

import clocker.docker.entity.util.DockerAttributes;

import com.google.common.reflect.TypeToken;
import com.google.inject.ImplementedBy;

import org.jclouds.net.domain.IpPermission;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.net.Cidr;

/**
 * An SDN provider implementation.
 */
@ImplementedBy(SdnProviderImpl.class)
public interface DockerSdnProvider extends SdnProvider {

    ConfigKey<Cidr> AGENT_CIDR = ConfigKeys.newConfigKey(Cidr.class, "sdn.agent.cidr", "CIDR for agent address allocation");

    AttributeSensor<Group> SDN_AGENTS = Sensors.newSensor(Group.class, "sdn.agents", "Group of SDN agent services");

    AttributeSensor<Integer> ALLOCATED_IPS = Sensors.newIntegerSensor("sdn.agent.ips", "Number of allocated IPs for agents");
    AttributeSensor<Map<String, InetAddress>> ALLOCATED_ADDRESSES = Sensors.newSensor(
            new TypeToken<Map<String, InetAddress>>() { }, "sdn.agent.addresses", "Allocated IP addresses for agents");

    @SetFromFlag("agentSpec")
    AttributeSensorAndConfigKey<EntitySpec<?>,EntitySpec<?>> SDN_AGENT_SPEC = ConfigKeys.newSensorAndConfigKey(
            new TypeToken<EntitySpec<?>>() { }, "sdn.agent.spec", "SDN agent specification");

    @SetFromFlag("dockerInfrastructure")
    AttributeSensorAndConfigKey<Entity, Entity> DOCKER_INFRASTRUCTURE = DockerAttributes.DOCKER_INFRASTRUCTURE;

    Collection<IpPermission> getIpPermissions(String source);

    DynamicCluster getDockerHostCluster();

    Group getAgents();

    InetAddress getNextAgentAddress(String agentId);

}
