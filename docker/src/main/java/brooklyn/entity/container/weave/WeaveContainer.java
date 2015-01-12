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

import brooklyn.config.ConfigKey;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.networking.subnet.SubnetTier;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.net.Cidr;

/**
 * The Weave container.
 */
@ImplementedBy(WeaveContainerImpl.class)
public interface WeaveContainer extends SoftwareProcess {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "0.8.0");

    @SetFromFlag("cidr")
    ConfigKey<Cidr> WEAVE_CIDR = ConfigKeys.newConfigKey(Cidr.class, "weave.cidr", "Weave CIDR for address allocation", Cidr.LINK_LOCAL);

    @SetFromFlag("weavePort")
    ConfigKey<Integer> WEAVE_PORT = ConfigKeys.newIntegerConfigKey("weave.port", "Weave port", 6783);

    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "https://raw.githubusercontent.com/zettio/weave/v${version}/weave");

    AttributeSensorAndConfigKey<DockerHost,DockerHost> DOCKER_HOST = ConfigKeys.newSensorAndConfigKey(DockerHost.class, "weave.docker.host", "Docker host we are running on");
    AttributeSensorAndConfigKey<WeaveInfrastructure,WeaveInfrastructure> WEAVE_INFRASTRUCTURE = ConfigKeys.newSensorAndConfigKey(WeaveInfrastructure.class, "weave.infrastructure", "Weave infrastructure entity");
 
    AttributeSensor<InetAddress> WEAVE_ADDRESS = Sensors.newSensor(InetAddress.class, "weave.address", "IP address of Weave service");
    AttributeSensor<WeaveContainer> WEAVE_CONTAINER = Sensors.newSensor(WeaveContainer.class, "weave.container.entity", "Weave service entity");

    DockerHost getDockerHost();

    MethodEffector<InetAddress> ATTACH_NETWORK = new MethodEffector<InetAddress>(WeaveContainer.class, "attachNetwork");

    /**
     * Attach a container to the Weave network.
     *
     * @param containerId the container ID
     * @return the {@link SubnetTier} IP address
     */
    @Effector(description="Attach a container to the Weave network")
    InetAddress attachNetwork(
            @EffectorParam(name="containerId", description="Container ID") String containerId);

}
