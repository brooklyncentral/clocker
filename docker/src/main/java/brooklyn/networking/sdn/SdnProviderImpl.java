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
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityPredicates;
import org.apache.brooklyn.core.feed.ConfigToAttributes;
import org.apache.brooklyn.core.location.Locations;
import org.apache.brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import org.apache.brooklyn.entity.group.BasicGroup;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.entity.group.DynamicGroup;
import org.apache.brooklyn.entity.stock.BasicStartableImpl;
import org.apache.brooklyn.entity.stock.DelegateEntity;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.QuorumCheck.QuorumChecks;
import org.apache.brooklyn.util.net.Cidr;

import brooklyn.entity.container.docker.DockerContainer;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.networking.VirtualNetwork;
import brooklyn.networking.location.NetworkProvisioningExtension;

public abstract class SdnProviderImpl extends BasicStartableImpl implements DockerSdnProvider {

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
                .displayName("SDN Managed Networks"));

        BasicGroup applications = addChild(EntitySpec.create(BasicGroup.class)
                .configure(BasicGroup.RUNNING_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty())
                .configure(BasicGroup.UP_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty())
                .displayName("SDN Networked Applications"));

        sensors().set(SDN_AGENTS, agents);
        sensors().set(SDN_NETWORKS, networks);
        sensors().set(SDN_APPLICATIONS, applications);

        synchronized (addressMutex) {
            sensors().set(ALLOCATED_IPS, 0);
            sensors().set(ALLOCATED_ADDRESSES, Maps.<String, InetAddress>newConcurrentMap());
            sensors().set(SUBNET_ADDRESS_ALLOCATIONS, Maps.<String, Integer>newConcurrentMap());
        }

        synchronized (networkMutex) {
            sensors().set(ALLOCATED_NETWORKS, 0);
            sensors().set(SUBNETS, Maps.<String, Cidr>newConcurrentMap());
        }

        sensors().set(SUBNET_ENTITIES, Maps.<String, VirtualNetwork>newConcurrentMap());
        sensors().set(CONTAINER_ADDRESSES, HashMultimap.<String, InetAddress>create());
    }

    @Override
    public InetAddress getNextAgentAddress(String agentId) {
        synchronized (addressMutex) {
            Cidr cidr = config().get(AGENT_CIDR);
            Integer allocated = sensors().get(ALLOCATED_IPS);
            InetAddress next = cidr.addressAtOffset(allocated + 1);
            sensors().set(ALLOCATED_IPS, allocated + 1);
            Map<String, InetAddress> addresses = sensors().get(ALLOCATED_ADDRESSES);
            addresses.put(agentId, next);
            sensors().set(ALLOCATED_ADDRESSES, addresses);
            return next;
        }
    }

    @Override
    public InetAddress getNextContainerAddress(String subnetId) {
        Cidr cidr = getSubnetCidr(subnetId);

        synchronized (addressMutex) {
            Map<String, Integer> allocations = sensors().get(SUBNET_ADDRESS_ALLOCATIONS);
            Integer allocated = allocations.get(subnetId);
            if (allocated == null) allocated = 1;
            InetAddress next = cidr.addressAtOffset(allocated + 1);
            allocations.put(subnetId, allocated + 1);
            sensors().set(SUBNET_ADDRESS_ALLOCATIONS, allocations);
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
            Cidr networkCidr = config().get(CONTAINER_NETWORK_CIDR);
            Integer networkSize = config().get(CONTAINER_NETWORK_SIZE);
            Integer allocated = sensors().get(ALLOCATED_NETWORKS);
            InetAddress baseAddress = networkCidr.addressAtOffset(allocated * (1 << (32 - networkSize)));
            Cidr subnetCidr = new Cidr(baseAddress.getHostAddress() + "/" + networkSize);
            LOG.debug("Allocated {} from {} for subnet #{}", new Object[] { subnetCidr, networkCidr, allocated });
            sensors().set(ALLOCATED_NETWORKS, allocated + 1);
            return subnetCidr;
        }
    }

    @Override
    public void recordSubnetCidr(String networkId, Cidr subnetCidr) {
        synchronized (networkMutex) {
            Map<String, Cidr> subnets = sensors().get(SdnProvider.SUBNETS);
            subnets.put(networkId, subnetCidr);
            sensors().set(SdnProvider.SUBNETS, subnets);
        }
    }

    @Override
    public void recordSubnetCidr(String networkId, Cidr subnetCidr, int allocated) {
        synchronized (networkMutex) {
            recordSubnetCidr(networkId, subnetCidr);
            Map<String, Integer> allocations = sensors().get(SUBNET_ADDRESS_ALLOCATIONS);
            allocations.put(networkId, allocated);
            sensors().set(SUBNET_ADDRESS_ALLOCATIONS, allocations);
        }
    }

    @Override
    public Cidr getSubnetCidr(String networkId) {
        synchronized (networkMutex) {
            Map<String, Cidr> subnets = sensors().get(SdnProvider.SUBNETS);
            return subnets.get(networkId);
        }
    }

    @Override
    public Object getNetworkMutex() { return networkMutex; }

    @Override
    public DynamicCluster getDockerHostCluster() {
        return config().get(DOCKER_INFRASTRUCTURE).sensors().get(DockerInfrastructure.DOCKER_HOST_CLUSTER);
    }

    @Override
    public Group getAgents() { return sensors().get(SDN_AGENTS); }

    public static class MemberTrackingPolicy extends AbstractMembershipTrackingPolicy {
        @Override
        protected void onEntityEvent(EventType type, Entity member) {
            ((SdnProviderImpl) entity).onHostChanged(member);
        }
    }

    @Override
    public void start(Collection<? extends Location> locs) {
        addLocations(locs);
        List<Location> locations = MutableList.copyOf(Locations.getLocationsCheckingAncestors(locs, this));

        sensors().set(SERVICE_UP, Boolean.FALSE);

        // Add ouserlves as an extension to the Docker location
        DockerInfrastructure infrastructure = (DockerInfrastructure) config().get(DOCKER_INFRASTRUCTURE);
        infrastructure.getDynamicLocation().addExtension(NetworkProvisioningExtension.class, this);

        super.start(locations);

        addHostTrackerPolicy();

        sensors().set(SERVICE_UP, Boolean.TRUE);
    }

    @Override
    public void stop() {
        sensors().set(SERVICE_UP, Boolean.FALSE);

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
            MemberTrackingPolicy hostTrackerPolicy = policies().add(PolicySpec.create(MemberTrackingPolicy.class)
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
            Boolean running = item.sensors().get(SERVICE_UP);
            if (exists && running && item.sensors().get(SdnAgent.SDN_AGENT) == null) {
                onHostAdded(item);
            } else if (!exists) {
                onHostRemoved(item);
            }
        }
    }

    /* Callbacks for Docker hosts using this SDN provider. */

    protected abstract void addHost(DockerHost host);

    protected abstract void removeHost(DockerHost host);

    @Override
    public Map<String, Cidr> listManagedNetworkAddressSpace() {
        return ImmutableMap.copyOf(sensors().get(SUBNETS));
    }

    @Override
    public void provisionNetwork(VirtualNetwork network) {
        // Call provisionNetwork on one of the agents to create it
        SdnAgent agent = (SdnAgent) (getAgents().getMembers().iterator().next());
        String networkId = agent.provisionNetwork(network);

        // Create a DynamicGroup with all attached entities
        EntitySpec<DynamicGroup> networkSpec = EntitySpec.create(DynamicGroup.class)
                .configure(DynamicGroup.ENTITY_FILTER, Predicates.and(
                        Predicates.not(Predicates.or(Predicates.instanceOf(DockerContainer.class), Predicates.instanceOf(DelegateEntity.class))),
                        EntityPredicates.attributeEqualTo(DockerContainer.DOCKER_INFRASTRUCTURE, sensors().get(DOCKER_INFRASTRUCTURE)),
                        SdnUtils.attachedToNetwork(networkId)))
                .displayName(network.getDisplayName());
        DynamicGroup subnet = sensors().get(SDN_APPLICATIONS).addMemberChild(networkSpec);
        subnet.sensors().set(VirtualNetwork.NETWORK_ID, networkId);
        network.sensors().set(VirtualNetwork.NETWORKED_APPLICATIONS, subnet);

        sensors().get(SDN_NETWORKS).addMember(network);
    }

    @Override
    public void deallocateNetwork(VirtualNetwork network) {
        String networkId = network.sensors().get(VirtualNetwork.NETWORK_ID);
        Optional<Entity> found = Iterables.tryFind(sensors().get(SDN_APPLICATIONS).getMembers(), EntityPredicates.attributeEqualTo(VirtualNetwork.NETWORK_ID, networkId));
        if (found.isPresent()) {
            Entity group = found.get();
            sensors().get(SDN_APPLICATIONS).removeMember(group);
            sensors().get(SDN_APPLICATIONS).removeChild(group);
            Entities.unmanage(group);
        } else {
            LOG.warn("Cannot find group containing {} network entities", networkId);
        }
        sensors().get(SDN_NETWORKS).removeMember(network);

        // TODO actually deprovision the network if possible?
    }

    static {
        RendererHints.register(SDN_AGENTS, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(SDN_NETWORKS, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(SDN_APPLICATIONS, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(DOCKER_INFRASTRUCTURE, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
    }

}
