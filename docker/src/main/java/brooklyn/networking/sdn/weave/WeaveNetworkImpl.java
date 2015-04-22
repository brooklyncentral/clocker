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
package brooklyn.networking.sdn.weave;

import java.util.Collection;

import org.jclouds.net.domain.IpPermission;
import org.jclouds.net.domain.IpProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.networking.sdn.SdnAgent;
import brooklyn.networking.sdn.SdnProvider;
import brooklyn.networking.sdn.SdnProviderImpl;
import brooklyn.util.collections.MutableList;
import brooklyn.util.net.Cidr;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableList;

public class WeaveNetworkImpl extends SdnProviderImpl implements WeaveNetwork {

    private static final Logger LOG = LoggerFactory.getLogger(WeaveNetwork.class);

    @Override
    public void init() {
        LOG.info("Starting Weave network id {}", getId());
        super.init();

        EntitySpec<?> agentSpec = EntitySpec.create(getConfig(SdnProvider.SDN_AGENT_SPEC, EntitySpec.create(WeaveContainer.class)))
                .configure(WeaveContainer.WEAVE_PORT, config().get(WeaveNetwork.WEAVE_PORT))
                .configure(WeaveContainer.SDN_PROVIDER, this);
        String weaveVersion = config().get(WEAVE_VERSION);
        if (Strings.isNonBlank(weaveVersion)) {
            agentSpec.configure(SoftwareProcess.SUGGESTED_VERSION, weaveVersion);
        }
        setAttribute(SdnProvider.SDN_AGENT_SPEC, agentSpec);

        Cidr weaveCidr = getNextSubnetCidr();
        config().set(AGENT_CIDR, weaveCidr);
    }

    @Override
    public Collection<IpPermission> getIpPermissions() {
        Collection<IpPermission> permissions = MutableList.of();
        Integer weavePort = config().get(WeaveContainer.WEAVE_PORT);
        IpPermission weaveTcpPort = IpPermission.builder()
                .ipProtocol(IpProtocol.TCP)
                .fromPort(weavePort)
                .toPort(weavePort)
                .cidrBlock(Cidr.UNIVERSAL.toString()) // TODO could be tighter restricted?
                .build();
        permissions.add(weaveTcpPort);
        IpPermission weaveUdpPort = IpPermission.builder()
                .ipProtocol(IpProtocol.UDP)
                .fromPort(weavePort)
                .toPort(weavePort)
                .cidrBlock(Cidr.UNIVERSAL.toString()) // TODO could be tighter restricted?
                .build();
        permissions.add(weaveUdpPort);
        return permissions;
    }

    @Override
    public void addHost(DockerHost host) {
        SshMachineLocation machine = host.getDynamicLocation().getMachine();
        EntitySpec<?> spec = EntitySpec.create(getAttribute(SDN_AGENT_SPEC))
                .configure(WeaveContainer.DOCKER_HOST, host);
        WeaveContainer agent = (WeaveContainer) getAgents().addChild(spec);
        Entities.manage(agent);
        getAgents().addMember(agent);
        agent.start(ImmutableList.of(machine));
        LOG.debug("{} added Weave service {}", this, agent);
    }

    @Override
    public void removeHost(DockerHost host) {
        SdnAgent agent = host.getAttribute(SdnAgent.SDN_AGENT);
        if (agent == null) {
            LOG.warn("{} cannot find Weave service: {}", this, host);
            return;
        }
        agent.stop();
        getAgents().removeMember(agent);
        Entities.unmanage(agent);
        LOG.debug("{} removed Weave service {}", this, agent);
    }

}