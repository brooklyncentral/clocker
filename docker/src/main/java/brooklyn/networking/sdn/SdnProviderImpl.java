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
import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.render.RendererHints;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.basic.BasicStartableImpl;
import brooklyn.entity.basic.DelegateEntity;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.container.DockerUtils;
import brooklyn.entity.container.docker.DockerContainer;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.location.Location;
import brooklyn.networking.VirtualNetwork;
import brooklyn.networking.location.NetworkProvisioningExtension;
import brooklyn.policy.PolicySpec;
import brooklyn.util.collections.QuorumCheck.QuorumChecks;
import brooklyn.util.net.Cidr;

import com.google.common.base.Predicates;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

public abstract class SdnProviderImpl extends BasicStartableImpl implements SdnProvider{

    private static final Logger LOG = LoggerFactory.getLogger(SdnProvider.class);

    /** Held while obtaining new IP addresses for containers. */
    protected transient final Object addressMutex = new Object[0];

    /** Held while adding or removing new {@link SdnAgent} entities on hosts. */
    protected transient final Object hostMutex = new Object[0];

    /** Mutex for provisioning new networks */
    protected transient final Object networkMutex = new Object[0];

    @Override
    public void init() {
        LOG.info("Starting SDN provider id {}", getId());
        super.init();

        ConfigToAttributes.apply(this, DOCKER_INFRASTRUCTURE);

        BasicGroup agents = addChild(EntitySpec.create(BasicGroup.class)
                .configure(BasicGroup.RUNNING_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty())
                .configure(BasicGroup.UP_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty())
                .displayName("SDN Host Agents"));

        BasicGroup networks = addChild(EntitySpec.create(BasicGroup.class)
                .configure(BasicGroup.RUNNING_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty())
                .configure(BasicGroup.UP_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty())
                .configure(BasicGroup.MEMBER_DELEGATE_CHILDREN, true)
                .displayName("SDN Managed Networks"));

        BasicGroup applications = addChild(EntitySpec.create(BasicGroup.class)
                .configure(BasicGroup.RUNNING_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty())
                .configure(BasicGroup.UP_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty())
                .displayName("SDN Networked Applications"));

        if (Entities.isManaged(this)) {
            Entities.manage(agents);
            Entities.manage(networks);
            Entities.manage(applications);
        }

        setAttribute(SDN_AGENTS, agents);
        setAttribute(SDN_NETWORKS, networks);
        setAttribute(SDN_APPLICATIONS, applications);

        synchronized (addressMutex) {
            setAttribute(ALLOCATED_IPS, 0);
            setAttribute(ALLOCATED_ADDRESSES, Maps.<String, InetAddress>newConcurrentMap());
            setAttribute(SUBNET_ADDRESS_ALLOCATIONS, Maps.<String, Integer>newConcurrentMap());
        }

        synchronized (networkMutex) {
            setAttribute(ALLOCATED_NETWORKS, 0);
            setAttribute(SUBNETS, Maps.<String, Cidr>newConcurrentMap());
        }

        setAttribute(SUBNET_ENTITIES, Maps.<String, VirtualNetwork>newConcurrentMap());
        setAttribute(CONTAINER_ADDRESSES, HashMultimap.<String, InetAddress>create());
    }

    @Override
    public InetAddress getNextAgentAddress(String agentId) {
        synchronized (addressMutex) {
            Cidr cidr = getConfig(AGENT_CIDR);
            Integer allocated = getAttribute(ALLOCATED_IPS);
            InetAddress next = cidr.addressAtOffset(allocated + 1);
            setAttribute(ALLOCATED_IPS, allocated + 1);
            Map<String, InetAddress> addresses = getAttribute(ALLOCATED_ADDRESSES);
            addresses.put(agentId, next);
            setAttribute(ALLOCATED_ADDRESSES, addresses);
            return next;
        }
    }

    @Override
    public InetAddress getNextContainerAddress(String subnetId) {
        Cidr cidr = getSubnetCidr(subnetId);

        synchronized (addressMutex) {
            Map<String, Integer> allocations = getAttribute(SUBNET_ADDRESS_ALLOCATIONS);
            Integer allocated = allocations.get(subnetId);
            if (allocated == null) allocated = 1;
            InetAddress next = cidr.addressAtOffset(allocated + 1);
            allocations.put(subnetId, allocated + 1);
            setAttribute(SUBNET_ADDRESS_ALLOCATIONS, allocations);
            return next;
        }
    }

    @Override
    public Cidr getNextSubnetCidr(String networkId) {
        synchronized (networkMutex) {
            Cidr networkCidr = getNextSubnetCidr();
            recordSubnetCidr(networkId, networkCidr);
            return networkCidr;
        }
    }

    @Override
    public Cidr getNextSubnetCidr() {
        synchronized (networkMutex) {
            Cidr networkCidr = getConfig(CONTAINER_NETWORK_CIDR);
            Integer networkSize = getConfig(CONTAINER_NETWORK_SIZE);
            Integer allocated = getAttribute(ALLOCATED_NETWORKS);
            InetAddress baseAddress = networkCidr.addressAtOffset(allocated * (1 << (32 - networkSize)));
            Cidr subnetCidr = new Cidr(baseAddress.getHostAddress() + "/" + networkSize);
            LOG.debug("Allocated {} from {} for subnet #{}", new Object[] { subnetCidr, networkCidr, allocated });
            setAttribute(ALLOCATED_NETWORKS, allocated + 1);
            return subnetCidr;
        }
    }

    @Override
    public void recordSubnetCidr(String networkId, Cidr subnetCidr) {
        synchronized (networkMutex) {
            Map<String, Cidr> subnets = getAttribute(SdnProvider.SUBNETS);
            subnets.put(networkId, subnetCidr);
            setAttribute(SdnProvider.SUBNETS, subnets);
        }
    }

    @Override
    public void recordSubnetCidr(String networkId, Cidr subnetCidr, int allocated) {
        synchronized (networkMutex) {
            recordSubnetCidr(networkId, subnetCidr);
            Map<String, Integer> allocations = getAttribute(SUBNET_ADDRESS_ALLOCATIONS);
            allocations.put(networkId, allocated);
            setAttribute(SUBNET_ADDRESS_ALLOCATIONS, allocations);
        }
    }

    @Override
    public Cidr getSubnetCidr(String networkId) {
        synchronized (networkMutex) {
            Map<String, Cidr> subnets = getAttribute(SdnProvider.SUBNETS);
            return subnets.get(networkId);
        }
    }

    @Override
    public Object getNetworkMutex() { return networkMutex; }

    @Override
    public DynamicCluster getDockerHostCluster() {
        return getConfig(DOCKER_INFRASTRUCTURE).getAttribute(DockerInfrastructure.DOCKER_HOST_CLUSTER);
    }

    @Override
    public Group getAgents() { return getAttribute(SDN_AGENTS); }

    public static class MemberTrackingPolicy extends AbstractMembershipTrackingPolicy {
        @Override protected void onEntityEvent(EventType type, Entity member) {
            ((SdnProviderImpl) super.entity).onHostChanged(member);
        }
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        setAttribute(SERVICE_UP, Boolean.FALSE);

        addHostTrackerPolicy();

        // Add ouserlves as an extension to the Docker location
        DockerInfrastructure infrastructure = (DockerInfrastructure) getConfig(DOCKER_INFRASTRUCTURE);
        infrastructure.getDynamicLocation().addExtension(NetworkProvisioningExtension.class, this);

        super.start(locations);

        setAttribute(SERVICE_UP, Boolean.TRUE);
    }

    @Override
    public void stop() {
        setAttribute(SERVICE_UP, Boolean.FALSE);

        super.stop();
    }

    @Override
    public void rebind() {
        super.rebind();
        // TODO implement custom SDN provider rebind logic
    }

    protected void addHostTrackerPolicy() {
        Group hosts = getDockerHostCluster();
        if (hosts != null) {
            MemberTrackingPolicy hostTrackerPolicy = addPolicy(PolicySpec.create(MemberTrackingPolicy.class)
                    .displayName("Docker host tracker")
                    .configure("group", hosts));
            LOG.info("Added policy {} to {}, during start", hostTrackerPolicy, this);
        }
    }

    private void onHostAdded(Entity item) {
        synchronized (hostMutex) {
            if (item instanceof DockerHost) {
                addHost((DockerHost) item);
            }
        }
    }

    private void onHostRemoved(Entity item) {
        synchronized (hostMutex) {
            if (item instanceof DockerHost) {
                removeHost((DockerHost) item);
            }
        }
    }

    private void onHostChanged(Entity item) {
        synchronized (hostMutex) {
            boolean exists = getDockerHostCluster().hasMember(item);
            Boolean running = item.getAttribute(SERVICE_UP);
            if (exists && running && item.getAttribute(SdnAgent.SDN_AGENT) == null) {
                onHostAdded(item);
            } else if (!exists) {
                onHostRemoved(item);
            }
        }
    }

    @Override
    public Map<String, Cidr> listManagedNetworkAddressSpace() {
        return ImmutableMap.copyOf(getAttribute(SUBNETS));
    }

    @Override
    public void provisionNetwork(VirtualNetwork network) {
        // Call provisionNetwork on one of the agents to create it
        SdnAgent agent = (SdnAgent) (getAgents().getMembers().iterator().next());
        String networkId = agent.provisionNetwork(network);

        // Create a DynamicGroup with all attached containers
        EntitySpec<DynamicGroup> networkSpec = EntitySpec.create(DynamicGroup.class)
                .configure(DynamicGroup.ENTITY_FILTER, Predicates.and(
                        Predicates.instanceOf(DockerContainer.class),
                        EntityPredicates.attributeEqualTo(DockerContainer.DOCKER_INFRASTRUCTURE, getAttribute(DOCKER_INFRASTRUCTURE)),
                        SdnAttributes.containerAttached(networkId)))
                .configure(DynamicGroup.MEMBER_DELEGATE_CHILDREN, true)
                .displayName(network.getDisplayName());
        DynamicGroup subnet = getAttribute(SDN_APPLICATIONS).addMemberChild(networkSpec);
        Entities.manage(subnet);
        Entities.deproxy(subnet).setAttribute(VirtualNetwork.NETWORK_ID, networkId);

        getAttribute(SDN_NETWORKS).addMember(network);
    }

    static {
        RendererHints.register(SDN_AGENTS, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(SDN_NETWORKS, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(SDN_APPLICATIONS, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(DOCKER_INFRASTRUCTURE, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
    }

}
