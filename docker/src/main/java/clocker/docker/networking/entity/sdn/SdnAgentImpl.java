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
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clocker.docker.entity.DockerHost;
import clocker.docker.entity.container.DockerContainer;
import clocker.docker.networking.entity.VirtualNetwork;
import clocker.docker.networking.entity.sdn.util.SdnUtils;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.core.feed.ConfigToAttributes;
import org.apache.brooklyn.entity.software.base.SoftwareProcessImpl;
import org.apache.brooklyn.entity.stock.DelegateEntity;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.net.Cidr;

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
    public VirtualNetwork createNetwork(String networkId) {
        final SdnProvider provider = sensors().get(SDN_PROVIDER);
        VirtualNetwork network = SdnUtils.createNetwork(provider, networkId);
        return network;
    }

    @Override
    public InetAddress attachNetwork(String containerId, final String networkId) {
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
        getDriver().createSubnet(networkId, subnetCidr);

        return networkId;
    }

    @Override
    public void deallocateNetwork(VirtualNetwork network) {
        String networkId = network.sensors().get(VirtualNetwork.NETWORK_ID);

        // Delete the network using the SDN driver
        getDriver().deleteSubnet(networkId);
    }

    @Override
    public void connect(DockerContainer container, VirtualNetwork network) {
        synchronized (network) {
            MutableSet<Entity> connected = MutableSet.copyOf(network.sensors().get(VirtualNetwork.CONNECTED_CONTAINERS));
            connected.add(container);
            network.sensors().set(VirtualNetwork.CONNECTED_CONTAINERS, connected.asImmutableCopy());
        }
        network.relations().add(VirtualNetwork.ATTACHED, container);
        container.relations().add(VirtualNetwork.CONNECTED, network);
    }

    @Override
    public void disconnect(DockerContainer container, VirtualNetwork network) {
        synchronized (network) {
            MutableSet<Entity> connected = MutableSet.copyOf(network.sensors().get(VirtualNetwork.CONNECTED_CONTAINERS));
            connected.remove(container);
            network.sensors().set(VirtualNetwork.CONNECTED_CONTAINERS, connected.asImmutableCopy());
        }
        network.relations().remove(VirtualNetwork.CONNECTED, container);
        container.relations().remove(VirtualNetwork.CONNECTED, network);
    }

    static {
        RendererHints.register(DOCKER_HOST, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(SDN_PROVIDER, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(SDN_AGENT, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
    }

}
