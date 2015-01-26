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
package brooklyn.entity.container.sdn.weave;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.container.sdn.SdnAgent;
import brooklyn.entity.container.sdn.SdnProvider;
import brooklyn.entity.container.sdn.SdnProviderImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableList;

public class WeaveNetworkImpl extends SdnProviderImpl implements WeaveNetwork {

    private static final Logger LOG = LoggerFactory.getLogger(SdnProvider.class);

    @Override
    public void init() {
        LOG.info("Starting Weave network id {}", getId());
        super.init();

        EntitySpec<?> agentSpec = EntitySpec.create(getConfig(SdnProvider.SDN_AGENT_SPEC, EntitySpec.create(WeaveContainer.class)))
                .configure(WeaveContainer.CIDR, getConfig(WeaveNetwork.CIDR))
                .configure(WeaveContainer.WEAVE_PORT, getConfig(WeaveNetwork.WEAVE_PORT))
                .configure(WeaveContainer.SDN_PROVIDER, this);
        String weaveVersion = getConfig(WEAVE_VERSION);
        if (Strings.isNonBlank(weaveVersion)) {
            agentSpec.configure(SoftwareProcess.SUGGESTED_VERSION, weaveVersion);
        }

        setAttribute(SdnProvider.SDN_AGENT_SPEC, agentSpec);
    }

    public void addHost(Entity item) {
        SshMachineLocation machine = ((DockerHost) item).getDynamicLocation().getMachine();
        EntitySpec<?> spec = EntitySpec.create(getAttribute(SDN_AGENT_SPEC))
                .configure(WeaveContainer.DOCKER_HOST, (DockerHost) item);
        WeaveContainer agent = (WeaveContainer) getAgents().addChild(spec);
        Entities.manage(agent);
        getAgents().addMember(agent);
        agent.start(ImmutableList.of(machine));
        if (LOG.isDebugEnabled()) LOG.debug("{} added weave service {}", this, agent);
    }

    public void removeHost(Entity item) {
        SdnAgent agent = item.getAttribute(SdnAgent.SDN_AGENT);
        if (agent == null) {
            LOG.warn("{} cannot find weave service: {}", this, item);
            return;
        }
        agent.stop();
        getAgents().removeMember(agent);
        Entities.unmanage(agent);
        if (LOG.isDebugEnabled()) LOG.debug("{} removed weave service {}", this, agent);
    }

}