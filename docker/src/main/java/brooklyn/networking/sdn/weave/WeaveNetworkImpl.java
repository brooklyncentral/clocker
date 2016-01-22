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
package brooklyn.networking.sdn.weave;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import org.jclouds.net.domain.IpPermission;
import org.jclouds.net.domain.IpProtocol;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.net.Cidr;
import org.apache.brooklyn.util.text.Strings;

import brooklyn.entity.container.docker.DockerHost;
import brooklyn.networking.sdn.SdnAgent;
import brooklyn.networking.sdn.SdnProviderImpl;

public class WeaveNetworkImpl extends SdnProviderImpl implements WeaveNetwork {

    private static final Logger LOG = LoggerFactory.getLogger(WeaveNetwork.class);

    @Override
    public void init() {
        LOG.info("Starting Weave network id {}", getId());
        super.init();

        EntitySpec<?> agentSpec = EntitySpec.create(config().get(WEAVE_ROUTER_SPEC))
                .configure(WeaveContainer.WEAVE_PORT, config().get(WeaveNetwork.WEAVE_PORT))
                .configure(WeaveContainer.SDN_PROVIDER, this);
        String weaveVersion = config().get(WEAVE_VERSION);
        if (Strings.isNonBlank(weaveVersion)) {
            agentSpec.configure(SoftwareProcess.SUGGESTED_VERSION, weaveVersion);
        }
        sensors().set(SDN_AGENT_SPEC, agentSpec);

        Cidr weaveCidr = getNextSubnetCidr();
        config().set(AGENT_CIDR, weaveCidr);
    }

    @Override
    public String getIconUrl() { return "classpath://weaveworks-logo.png"; }

    @Override
    public Collection<IpPermission> getIpPermissions(String source) {
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
        Integer proxyPort = config().get(WeaveContainer.WEAVE_PROXY_PORT);
        IpPermission proxyTcpPort = IpPermission.builder()
                .ipProtocol(IpProtocol.TCP)
                .fromPort(proxyPort)
                .toPort(proxyPort)
                .cidrBlock(source)
                .build();
        permissions.add(proxyTcpPort);
        return permissions;
    }

    @Override
    public void addHost(DockerHost host) {
        SshMachineLocation machine = host.getDynamicLocation().getMachine();
        EntitySpec<?> spec = EntitySpec.create(sensors().get(SDN_AGENT_SPEC))
                .configure(WeaveContainer.DOCKER_HOST, host);
        WeaveContainer agent = (WeaveContainer) getAgents().addChild(spec);
        getAgents().addMember(agent);
        agent.start(ImmutableList.of(machine));
        LOG.debug("{} added Weave service {}", this, agent);
    }

    @Override
    public void removeHost(DockerHost host) {
        SdnAgent agent = host.sensors().get(SdnAgent.SDN_AGENT);
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
