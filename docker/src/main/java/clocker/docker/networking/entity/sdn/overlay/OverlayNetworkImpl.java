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
package clocker.docker.networking.entity.sdn.overlay;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clocker.docker.entity.DockerHost;
import clocker.docker.networking.entity.sdn.SdnAgent;
import clocker.docker.networking.entity.sdn.SdnProviderImpl;

import com.google.common.collect.ImmutableList;

import org.jclouds.net.domain.IpPermission;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.feed.ConfigToAttributes;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.exceptions.Exceptions;

public class OverlayNetworkImpl extends SdnProviderImpl implements OverlayNetwork {

    private static final Logger LOG = LoggerFactory.getLogger(OverlayNetwork.class);

    @Override
    public void init() {
        LOG.info("Starting Overlay network id {}", getId());
        super.init();

        ConfigToAttributes.apply(this, SDN_AGENT_SPEC);
    }

    @Override
    public String getIconUrl() { return "classpath://docker-logo.png"; }

    @Override
    public Collection<IpPermission> getIpPermissions(String source) {
        Collection<IpPermission> permissions = MutableList.of();
        return permissions;
    }

    @Override
    public InetAddress getNextAgentAddress(String agentId) {
        Entity agent = getManagementContext().getEntityManager().getEntity(agentId);
        String address = agent.sensors().get(OverlayPlugin.DOCKER_HOST).sensors().get(Attributes.SUBNET_ADDRESS);
        try {
            return InetAddress.getByName(address);
        } catch (UnknownHostException uhe) {
            throw Exceptions.propagate(uhe);
        }
    }

    @Override
    public void addHost(DockerHost host) {
        SshMachineLocation machine = host.getDynamicLocation().getMachine();
        EntitySpec<?> spec = EntitySpec.create(sensors().get(SDN_AGENT_SPEC))
                .configure(OverlayPlugin.SDN_PROVIDER, this)
                .configure(OverlayPlugin.DOCKER_HOST, host);
        OverlayPlugin agent = (OverlayPlugin) getAgents().addChild(spec);
        getAgents().addMember(agent);
        agent.start(ImmutableList.of(machine));
        if (LOG.isDebugEnabled()) LOG.debug("{} added overlay plugin {}", this, agent);
    }

    @Override
    public void removeHost(DockerHost host) {
        SdnAgent agent = host.sensors().get(SdnAgent.SDN_AGENT);
        if (agent == null) {
            LOG.warn("{} cannot find overlay service: {}", this, host);
            return;
        }
        agent.stop();
        getAgents().removeMember(agent);
        Entities.unmanage(agent);
        if (LOG.isDebugEnabled()) LOG.debug("{} removed overlay plugin {}", this, agent);
    }

}
