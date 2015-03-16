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
package brooklyn.networking.sdn.calico;

import java.util.Collection;

import org.jclouds.net.domain.IpPermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.group.Cluster;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.nosql.etcd.EtcdCluster;
import brooklyn.entity.nosql.etcd.EtcdNode;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.networking.sdn.SdnAgent;
import brooklyn.networking.sdn.SdnProvider;
import brooklyn.networking.sdn.SdnProviderImpl;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.QuorumCheck.QuorumChecks;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

public class CalicoNetworkImpl extends SdnProviderImpl implements CalicoNetwork {

    private static final Logger LOG = LoggerFactory.getLogger(CalicoNetwork.class);

    @Override
    public void init() {
        LOG.info("Starting Calico network id {}", getId());
        super.init();

        EntitySpec<?> etcdNodeSpec = EntitySpec.create(getConfig(EtcdCluster.ETCD_NODE_SPEC, EntitySpec.create(EtcdNode.class)));
        String etcdVersion = config().get(ETCD_VERSION);
        if (Strings.isNonBlank(etcdVersion)) {
            etcdNodeSpec.configure(SoftwareProcess.SUGGESTED_VERSION, etcdVersion);
        }
        setAttribute(EtcdCluster.ETCD_NODE_SPEC, etcdNodeSpec);

        EtcdCluster etcd = addChild(EntitySpec.create(EtcdCluster.class)
                .configure(Cluster.INITIAL_SIZE, 0)
                .configure(EtcdCluster.ETCD_NODE_SPEC, etcdNodeSpec)
                .configure(DynamicCluster.QUARANTINE_FAILED_ENTITIES, true)
                .configure(DynamicCluster.RUNNING_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty())
                .configure(DynamicCluster.UP_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty())
                .displayName("Calico Etcd Cluster"));

        if (Entities.isManaged(this)) {
            Entities.manage(etcd);
        }

        setAttribute(ETCD_CLUSTER, etcd);

        EntitySpec<?> agentSpec = EntitySpec.create(getConfig(SdnProvider.SDN_AGENT_SPEC, EntitySpec.create(CalicoPlugin.class)))
                .configure(CalicoPlugin.SDN_PROVIDER, this);
        String calicoVersion = getConfig(CALICO_VERSION);
        if (Strings.isNonBlank(calicoVersion)) {
            agentSpec.configure(SoftwareProcess.SUGGESTED_VERSION, calicoVersion);
        }
        setAttribute(SdnProvider.SDN_AGENT_SPEC, agentSpec);
    }

    @Override
    public Collection<IpPermission> getIpPermissions() {
        Collection<IpPermission> permissions = MutableList.of();
        return permissions;
    }

    @Override
    public void start(Collection<? extends Location> locations) {

        super.start(locations);
    }

    @Override
    public void rebind() {
        super.rebind();
        // TODO implement calico rebind logic
    }

    @Override
    public void addHost(DockerHost host) {
        SshMachineLocation machine = host.getDynamicLocation().getMachine();

        EtcdCluster etcd = getAttribute(ETCD_CLUSTER);
        EtcdNode node = (EtcdNode) etcd.addNode(machine, Maps.newHashMap());
        node.start(ImmutableList.of(machine));

        EntitySpec<?> spec = EntitySpec.create(getAttribute(SDN_AGENT_SPEC))
                .configure(CalicoPlugin.DOCKER_HOST, host)
                .configure(CalicoPlugin.ETCD_NODE, node);
        CalicoPlugin agent = (CalicoPlugin) getAgents().addChild(spec);
        Entities.manage(agent);
        getAgents().addMember(agent);
        agent.start(ImmutableList.of(machine));
        if (LOG.isDebugEnabled()) LOG.debug("{} added calico plugin {}", this, agent);
    }

    @Override
    public void removeHost(DockerHost host) {
        SdnAgent agent = host.getAttribute(SdnAgent.SDN_AGENT);
        if (agent == null) {
            LOG.warn("{} cannot find calico service: {}", this, host);
            return;
        }

        EtcdCluster etcd = getAttribute(ETCD_CLUSTER);
        EtcdNode node = agent.getAttribute(CalicoPlugin.ETCD_NODE);
        etcd.removeMember(node);
        node.stop();

        agent.stop();
        getAgents().removeMember(agent);
        Entities.unmanage(agent);
        if (LOG.isDebugEnabled()) LOG.debug("{} removed calico plugin {}", this, agent);
    }

}