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
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.net.Cidr;

import com.google.common.base.Supplier;
import com.google.common.reflect.TypeToken;

/**
 * An SDN provider implementation.
 */
public interface SdnProvider extends BasicStartable, Supplier<InetAddress> {

    @SetFromFlag("cidr")
    ConfigKey<Cidr> CIDR = ConfigKeys.newConfigKey(Cidr.class, "sdn.cidr", "CIDR for address allocation");

    AttributeSensor<Group> SDN_AGENTS = Sensors.newSensor(Group.class, "sdn.agents", "Group of SDN agent services");
    AttributeSensor<Integer> ALLOCATED_IPS = Sensors.newIntegerSensor("sdn.allocated.ips", "Number of allocated IPs");

    @SetFromFlag("agentSpec")
    AttributeSensorAndConfigKey<EntitySpec<?>,EntitySpec<?>> SDN_AGENT_SPEC = ConfigKeys.newSensorAndConfigKey(
            new TypeToken<EntitySpec<?>>() { },
            "sdn.agent.spec", "SDN agent specification");

    @SetFromFlag("dockerInfrastructure")
    AttributeSensorAndConfigKey<Entity, Entity> DOCKER_INFRASTRUCTURE = DockerAttributes.DOCKER_INFRASTRUCTURE;

    Collection<IpPermission> getIpPermissions();

    DynamicCluster getDockerHostCluster();

    Group getAgents();

    void addHost(Entity host);

    void removeHost(Entity host);
}
