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
package brooklyn.networking.sdn;

import java.net.InetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.core.feed.ConfigToAttributes;
import org.apache.brooklyn.entity.software.base.SoftwareProcessImpl;
import org.apache.brooklyn.entity.stock.DelegateEntity;
import org.apache.brooklyn.util.net.Cidr;

import brooklyn.entity.container.docker.DockerHost;
import brooklyn.networking.VirtualNetwork;

/**
 * An SDN agent process on a Docker host.
 */
public abstract class SdnAgentImpl extends SoftwareProcessImpl implements SdnAgent {

    private static final Logger LOG = LoggerFactory.getLogger(SdnAgent.class);

    @Override
    public void init() {
        super.init();

        ConfigToAttributes.apply(this, DOCKER_HOST);
        ConfigToAttributes.apply(this, SDN_PROVIDER);
    }

    @Override
    public SdnAgentDriver getDriver() {
        return (SdnAgentDriver) super.getDriver();
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        super.connectServiceUpIsRunning();
    }

    @Override
    public void disconnectSensors() {
        super.disconnectServiceUpIsRunning();
        super.disconnectSensors();
    }

    @Override
    public DockerHost getDockerHost() {
        return sensors().get(DOCKER_HOST);
    }

    @Override
    public void preStart() {
        InetAddress address = ((DockerSdnProvider) sensors().get(SDN_PROVIDER)).getNextAgentAddress(getId());
        sensors().set(SDN_AGENT_ADDRESS, address);
    }

    @Override
    public void postStart() {
        getDockerHost().sensors().set(SDN_AGENT, this);
    }

    @Override
    public void rebind() {
        super.rebind();
        // TODO implement custom SDN agent rebind logic
    }

    @Override
    public InetAddress attachNetwork(String containerId, final String networkId) {
        final SdnProvider provider = sensors().get(SDN_PROVIDER);
        VirtualNetwork network = SdnUtils.createNetwork(provider, networkId);

        InetAddress address = getDriver().attachNetwork(containerId, networkId);
        LOG.info("Attached container ID {} to {}: {}", new Object[] { containerId, networkId,  address.getHostAddress() });

        return address;
    }

    @Override
    public String provisionNetwork(VirtualNetwork network) {
        String networkId = network.sensors().get(VirtualNetwork.NETWORK_ID);

        SdnProvider provider = sensors().get(SDN_PROVIDER);
        Cidr subnetCidr = SdnUtils.provisionNetwork(provider, network);

        // Create the network using the SDN driver
        getDriver().createSubnet(network.getId(), networkId, subnetCidr);

        return networkId;
    }

    static {
        RendererHints.register(DOCKER_HOST, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(SDN_PROVIDER, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(SDN_AGENT, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
    }

}
