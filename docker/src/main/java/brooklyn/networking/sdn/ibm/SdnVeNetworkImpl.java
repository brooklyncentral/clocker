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
package brooklyn.networking.sdn.ibm;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.jclouds.net.domain.IpPermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.docker.DockerLocation;
import brooklyn.networking.ManagedNetwork;
import brooklyn.networking.location.NetworkProvisioningExtension;
import brooklyn.networking.sdn.SdnAgent;
import brooklyn.networking.sdn.SdnProvider;
import brooklyn.networking.sdn.SdnProviderImpl;
import brooklyn.util.collections.MutableList;
import brooklyn.util.net.Cidr;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class SdnVeNetworkImpl extends SdnProviderImpl implements SdnVeNetwork, NetworkProvisioningExtension {

    private static final Logger LOG = LoggerFactory.getLogger(SdnProvider.class);

    @Override
    public void init() {
        LOG.info("Starting IBM SDN VE network id {}", getId());
        super.init();

        EntitySpec<?> agentSpec = EntitySpec.create(getConfig(SdnProvider.SDN_AGENT_SPEC, EntitySpec.create(SdnVeAgent.class)))
                .configure(SdnVeAgent.SDN_PROVIDER, this);

        setAttribute(SdnProvider.SDN_AGENT_SPEC, agentSpec);
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        super.start(locations);

        DockerLocation dockerLocation = Iterables.getOnlyElement(Iterables.filter(getLocations(), DockerLocation.class));
        dockerLocation.addExtension(NetworkProvisioningExtension.class, this);
    }

    @Override
    public Collection<IpPermission> getIpPermissions() {
        Collection<IpPermission> permissions = MutableList.of();
        return permissions;
    }

    @Override
    public void addHost(DockerHost host) {
        SshMachineLocation machine = host.getDynamicLocation().getMachine();
        EntitySpec<?> spec = EntitySpec.create(getAttribute(SDN_AGENT_SPEC))
                .configure(SdnVeAgent.DOCKER_HOST, host);
        SdnVeAgent agent = (SdnVeAgent) getAgents().addChild(spec);
        Entities.manage(agent);
        getAgents().addMember(agent);
        agent.start(ImmutableList.of(machine));
        if (LOG.isDebugEnabled()) LOG.debug("{} added IBM SDN VE agent {}", this, agent);
    }

    @Override
    public void removeHost(DockerHost host) {
        SdnAgent agent = host.getAttribute(SdnAgent.SDN_AGENT);
        if (agent == null) {
            LOG.warn("{} cannot find dove agent: {}", this, host);
            return;
        }
        agent.stop();
        getAgents().removeMember(agent);
        Entities.unmanage(agent);
        if (LOG.isDebugEnabled()) LOG.debug("{} removed IBM SDN VE agent {}", this, agent);
    }

    @Override
    public Map<String, Cidr> listManagedNetworkAddressSpace() {
        return null;
    }

    @Override
    public Set<ManagedNetwork> getNetworks() {
        return ImmutableSet.copyOf(getAttribute(SUBNET_ENTITIES).values());
    }

    @Override
    public ManagedNetwork addNetwork(String subnetId, Cidr subnetCidr, Map<String, Object> flags) {
        synchronized (addressMutex) {
            Map<String, ManagedNetwork> networks = getAttribute(SUBNET_ENTITIES);
            if (networks.containsKey(subnetId)) return networks.get(subnetId);

            Map<String, Cidr> subnets = getAttribute(SUBNETS);
            subnets.put(subnetId, subnetCidr);
            setAttribute(SUBNETS, subnets);

            EntitySpec<SdnVeSubnet> networkSpec = EntitySpec.create(SdnVeSubnet.class);
            SdnVeSubnet subnet = getAttribute(SDN_NETWORKS).addMemberChild(networkSpec);
            Entities.manage(subnet);

            networks.put(subnetId, subnet);
            setAttribute(SUBNET_ENTITIES, networks);
        }
    }

}
