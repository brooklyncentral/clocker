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
import brooklyn.entity.Group;
import brooklyn.entity.basic.BasicStartable;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.location.PortRange;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.net.Cidr;

import com.google.common.base.Supplier;

/**
 * A collection of machines running Weave.
 */
@Catalog(name = "Weave Infrastructure", description = "Weave SDN.")
@ImplementedBy(WeaveInfrastructureImpl.class)
public interface WeaveInfrastructure extends BasicStartable, Supplier<InetAddress> {

    ConfigKey<Boolean> ENABLED = ConfigKeys.newBooleanConfigKey("weave.enabled",  "Enable Weave SDN", Boolean.TRUE);

    @SetFromFlag("version")
    ConfigKey<String> WEAVE_VERSION = WeaveContainer.SUGGESTED_VERSION;

    @SetFromFlag("cidr")
    ConfigKey<Cidr> WEAVE_CIDR = WeaveContainer.WEAVE_CIDR;

    @SetFromFlag("weavePort")
    ConfigKey<PortRange> WEAVE_PORT = WeaveContainer.WEAVE_PORT.getConfigKey();

    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> WEAVE_DOWNLOAD_URL = WeaveContainer.DOWNLOAD_URL;

    AttributeSensor<Group> WEAVE_SERVICES = Sensors.newSensor(Group.class, "weave.services", "Group of Weave services");

    AttributeSensor<Integer> ALLOCATED_IPS = Sensors.newIntegerSensor("weave.ips", "Number of allocated IPs");

    AttributeSensorAndConfigKey<DockerInfrastructure, DockerInfrastructure> DOCKER_INFRASTRUCTURE = DockerHost.DOCKER_INFRASTRUCTURE;

    DynamicCluster getDockerHostCluster();

    Group getWeaveServices();

}
