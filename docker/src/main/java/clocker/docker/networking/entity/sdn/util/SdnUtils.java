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
package clocker.docker.networking.entity.sdn.util;

import java.util.List;
import java.util.concurrent.Callable;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clocker.docker.entity.DockerHost;
import clocker.docker.networking.entity.VirtualNetwork;
import clocker.docker.networking.entity.sdn.SdnProvider;

import com.google.common.base.CharMatcher;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityPredicates;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.TaskBuilder;
import org.apache.brooklyn.util.net.Cidr;
import org.apache.brooklyn.util.repeat.Repeater;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;

public class SdnUtils {

    private static final Logger LOG = LoggerFactory.getLogger(SdnUtils.class);

    /** Do not instantiate. */
    private SdnUtils() { }

    /** Checks if the SDN provider is of the specified type. */
    public static final boolean isSdnProvider(Entity entity, String providerName) {
        if (entity.config().get(SdnAttributes.SDN_ENABLE)) {
            Entity sdn = entity.sensors().get(SdnAttributes.SDN_PROVIDER);
            if (sdn == null) return false;
            return sdn.getEntityType().getSimpleName().equalsIgnoreCase(providerName);
        } else return false;
    }

    public static final VirtualNetwork createNetwork(final SdnProvider provider, final String networkId) {
        boolean createNetwork = false;
        Cidr subnetCidr = null;
        VirtualNetwork network = null;
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
                    .configure(SdnAttributes.SDN_PROVIDER, provider)
                    .configure(VirtualNetwork.NETWORK_ID, networkId)
                    .configure(VirtualNetwork.NETWORK_CIDR, subnetCidr);

            // Start and then add this virtual network as a child of SDN_NETWORKS
            network = provider.sensors().get(SdnProvider.SDN_NETWORKS).addChild(networkSpec);
            Entities.start(network, provider.getLocations());
            Entities.waitForServiceUp(network);
        } else {
            network = lookupNetwork(provider, networkId);
        }

        return network;
    }

    public static final VirtualNetwork lookupNetwork(final SdnProvider provider, final String networkId) {
        Task<Boolean> lookup = TaskBuilder.<Boolean> builder()
                .displayName("Waiting until virtual network is available")
                .body(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        return Repeater.create()
                                .every(Duration.TEN_SECONDS)
                                .until(new Callable<Boolean>() {
                                    public Boolean call() {
                                        Optional<Entity> found = Iterables.tryFind(provider.sensors().get(SdnProvider.SDN_NETWORKS).getMembers(),
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
        VirtualNetwork network = (VirtualNetwork) Iterables.find(provider.sensors().get(SdnProvider.SDN_NETWORKS).getMembers(),
                EntityPredicates.attributeEqualTo(VirtualNetwork.NETWORK_ID, networkId));
        return network;
    }

    public static final Cidr provisionNetwork(final SdnProvider provider, final VirtualNetwork network) {
        String networkId = network.sensors().get(VirtualNetwork.NETWORK_ID);

        // Record the network CIDR being provisioned, allocating if required
        Cidr subnetCidr = network.config().get(VirtualNetwork.NETWORK_CIDR);
        if (subnetCidr == null) {
            subnetCidr = provider.getNextSubnetCidr(networkId);
        } else {
            provider.recordSubnetCidr(networkId, subnetCidr);
        }
        network.sensors().set(VirtualNetwork.NETWORK_CIDR, subnetCidr);

        return subnetCidr;
    }

    public static final Predicate<Entity> attachedToNetwork(String networkId) {
        Preconditions.checkNotNull(networkId, "networkId");
        return new AttachedToNetworkPredicate(networkId);
    }

    public static class AttachedToNetworkPredicate implements Predicate<Entity> {

        private final String id;

        public AttachedToNetworkPredicate(String id) {
            this.id = Preconditions.checkNotNull(id, "id");
        }

        @Override
        public boolean apply(@Nullable Entity input) {
            List<String> networks = input.sensors().get(SdnAttributes.ATTACHED_NETWORKS);
            if (networks != null) {
                return networks.contains(id);
            } else {
                return false;
            }
        }
    }

    public static final int countAttached(DockerHost dockerHost, String networkId) {
        Entity etcd = dockerHost.sensors().get(DockerHost.ETCD_NODE);
        String installDir = etcd.sensors().get(SoftwareProcess.EXPANDED_INSTALL_DIR);
        String fullNetworkId = dockerHost.runDockerCommand(String.format("network inspect --format=\"{{ .ID }}\" %s", networkId));
        String output = dockerHost.execCommand(String.format("%s/etcdctl ls /docker/network/v1.0/endpoint/%s", installDir, fullNetworkId));
        int attached = Iterables.size(Splitter.on("\n").split(output));
        return attached;
    }

}
