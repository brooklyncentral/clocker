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
package brooklyn.entity.container.weave;

import java.net.InetAddress;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.BasicStartable;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.net.Cidr;

import com.google.common.base.Supplier;
import com.google.common.reflect.TypeToken;

/**
 * A collection of machines running Weave.
 */
@Catalog(name = "Weave Infrastructure", description = "Weave SDN.")
@ImplementedBy(WeaveInfrastructureImpl.class)
public interface WeaveInfrastructure extends BasicStartable, Supplier<InetAddress> {

    @SetFromFlag("version")
    ConfigKey<String> WEAVE_VERSION = WeaveContainer.SUGGESTED_VERSION;

    @SetFromFlag("cidr")
    ConfigKey<Cidr> WEAVE_CIDR = WeaveContainer.WEAVE_CIDR;

    @SetFromFlag("weavePort")
    ConfigKey<Integer> WEAVE_PORT = WeaveContainer.WEAVE_PORT;

    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> WEAVE_DOWNLOAD_URL = WeaveContainer.DOWNLOAD_URL;

    AttributeSensor<Group> WEAVE_SERVICES = Sensors.newSensor(Group.class, "weave.services", "Group of Weave services");
    AttributeSensor<Integer> ALLOCATED_IPS = Sensors.newIntegerSensor("weave.ips", "Number of allocated IPs");

    @SetFromFlag("weaveContainerSpec")
    AttributeSensorAndConfigKey<EntitySpec<WeaveContainer>,EntitySpec<WeaveContainer>> WEAVE_CONTAINER_SPEC = ConfigKeys.newSensorAndConfigKey(
            new TypeToken<EntitySpec<WeaveContainer>>() { },
            "weave.container.spec", "Weave container specification", EntitySpec.create(WeaveContainer.class));

    @SetFromFlag("dockerInfrastructure")
    AttributeSensorAndConfigKey<Entity, Entity> DOCKER_INFRASTRUCTURE = DockerHost.DOCKER_INFRASTRUCTURE;

    DynamicCluster getDockerHostCluster();

    Group getWeaveServices();

}
