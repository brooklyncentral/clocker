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
import java.util.Collections;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.render.RendererHints;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.DelegateEntity;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.management.Task;
import brooklyn.networking.VirtualNetwork;
import brooklyn.util.net.Cidr;
import brooklyn.util.repeat.Repeater;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.TaskBuilder;
import brooklyn.util.time.Duration;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;

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
        return getAttribute(DOCKER_HOST);
    }

    @Override
    public void preStart() {
        InetAddress address = getAttribute(SDN_PROVIDER).getNextAgentAddress(getId());
        setAttribute(SDN_AGENT_ADDRESS, address);
    }

    @Override
    public void postStart() {
        ((EntityLocal) getDockerHost()).setAttribute(SDN_AGENT, this);
    }

    @Override
    public void rebind() {
        super.rebind();
        // TODO implement custom SDN agent rebind logic
    }

    @Override
    public InetAddress attachNetwork(String containerId, final String networkId) {
        final SdnProvider provider = getAttribute(SDN_PROVIDER);
        boolean createNetwork = false;
        Cidr subnetCidr = null;
        synchronized (provider.getNetworkMutex()) {
            subnetCidr = provider.getSubnetCidr(networkId);
            if (subnetCidr == null) {
                subnetCidr = provider.getNextSubnetCidr(networkId);
                createNetwork = true;
            }
        }
        if (createNetwork) {
            // Get a CIDR for the subnet from the availabkle pool and create a virtual network
            EntitySpec<VirtualNetwork> networkSpec = EntitySpec.create(VirtualNetwork.class)
                    .configure(VirtualNetwork.NETWORK_ID, networkId)
                    .configure(VirtualNetwork.NETWORK_CIDR, subnetCidr);

            // Start and then add this virtual network as a child of SDN_NETWORKS
            VirtualNetwork network = provider.getAttribute(SdnProvider.SDN_NETWORKS).addChild(networkSpec);
            Entities.manage(network);
            Entities.start(network, Collections.singleton(((DockerInfrastructure) provider.getAttribute(SdnProvider.DOCKER_INFRASTRUCTURE)).getDynamicLocation()));
            Entities.waitForServiceUp(network);
        } else {
            Task<Boolean> lookup = TaskBuilder.<Boolean> builder()
                    .name("Waiting until virtual network is available")
                    .body(new Callable<Boolean>() {
                        @Override
                        public Boolean call() throws Exception {
                            return Repeater.create()
                                    .every(Duration.TEN_SECONDS)
                                    .until(new Callable<Boolean>() {
                                        public Boolean call() {
                                            Optional<Entity> found = Iterables.tryFind(provider.getAttribute(SdnProvider.SDN_NETWORKS).getMembers(),
                                                    EntityPredicates.attributeEqualTo(VirtualNetwork.NETWORK_ID, networkId));
                                            return found.isPresent();
                                        }
                                    })
                                    .limitTimeTo(Duration.ONE_MINUTE)
                                    .run();
                        }
                    })
                    .build();
            Boolean result = DynamicTasks.queueIfPossible(lookup)
                    .orSubmitAndBlock()
                    .andWaitForSuccess();
            if (!result) {
                throw new IllegalStateException(String.format("Cannot find virtual network entity for %s", networkId));
            }
        }

        InetAddress address = getDriver().attachNetwork(containerId, networkId);
        LOG.info("Attached container ID {} to {}: {}", new Object[] { containerId, networkId,  address.getHostAddress() });

        Multimap<String, InetAddress> addresses = provider.getAttribute(SdnProvider.CONTAINER_ADDRESSES);
        addresses.put(containerId, address);
        ((EntityLocal) provider).setAttribute(SdnProvider.CONTAINER_ADDRESSES, addresses);

        // Rescan SDN network groups for containers
        DynamicGroup network = (DynamicGroup) Iterables.find(provider.getAttribute(SdnProvider.SDN_APPLICATIONS).getMembers(),
                EntityPredicates.attributeEqualTo(VirtualNetwork.NETWORK_ID, networkId));
        network.rescanEntities();

        return address;
    }

    @Override
    public String provisionNetwork(VirtualNetwork network) {
        String networkId = network.getAttribute(VirtualNetwork.NETWORK_ID);

        // Record the network CIDR being provisioned, allocating if required
        Cidr subnetCidr = network.config().get(VirtualNetwork.NETWORK_CIDR);
        if (subnetCidr == null) {
            subnetCidr = getAttribute(SDN_PROVIDER).getNextSubnetCidr(networkId);
        } else {
            getAttribute(SDN_PROVIDER).recordSubnetCidr(networkId, subnetCidr);
        }
        ((EntityLocal) network).setAttribute(VirtualNetwork.NETWORK_CIDR, subnetCidr);

        // Create the netwoek using the SDN driver
        getDriver().createSubnet(network.getId(), networkId, subnetCidr);

        return networkId;
    }

    static {
        RendererHints.register(DOCKER_HOST, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(SDN_PROVIDER, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(SDN_AGENT, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
    }

}
