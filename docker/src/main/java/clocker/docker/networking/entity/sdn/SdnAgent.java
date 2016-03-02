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

import clocker.docker.entity.DockerHost;
import clocker.docker.networking.entity.VirtualNetwork;

import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.annotation.Effector;
import org.apache.brooklyn.core.annotation.EffectorParam;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.effector.MethodEffector;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import brooklyn.networking.subnet.SubnetTier;

/**
 * An SDN agent process.
 */
public interface SdnAgent extends SoftwareProcess {

    @SetFromFlag("host")
    AttributeSensorAndConfigKey<DockerHost,DockerHost> DOCKER_HOST = ConfigKeys.newSensorAndConfigKey(DockerHost.class, "sdn.agent.docker.host", "Docker host we are running on");

    @SetFromFlag("provider")
    AttributeSensorAndConfigKey<SdnProvider,SdnProvider> SDN_PROVIDER = ConfigKeys.newSensorAndConfigKey(SdnProvider.class, "sdn.provider", "SDN provider entity");
 
    AttributeSensor<InetAddress> SDN_AGENT_ADDRESS = Sensors.newSensor(InetAddress.class, "sdn.agent.address", "IP address of SDN agent service");
    AttributeSensor<SdnAgent> SDN_AGENT = Sensors.newSensor(SdnAgent.class, "sdn.agent.entity", "SDN agent entity");

    DockerHost getDockerHost();

    String provisionNetwork(VirtualNetwork network);

    MethodEffector<InetAddress> ATTACH_NETWORK = new MethodEffector<InetAddress>(SdnAgent.class, "attachNetwork");

    /**
     * Attach a container to a network.
     *
     * @param containerId the container ID
     * @param networkId the network ID to attach
     * @return the {@link SubnetTier} IP address
     */
    @Effector(description="Attach a container to a network")
    InetAddress attachNetwork(
            @EffectorParam(name="containerId", description="Container ID") String containerId,
            @EffectorParam(name="networkId", description="Network ID") String networkId);

}
