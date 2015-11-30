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

import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityPredicates;
import org.apache.brooklyn.entity.group.DynamicGroup;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.TaskBuilder;
import org.apache.brooklyn.util.net.Cidr;
import org.apache.brooklyn.util.repeat.Repeater;
import org.apache.brooklyn.util.time.Duration;

import brooklyn.networking.VirtualNetwork;

public class SdnUtils {

    private static final Logger LOG = LoggerFactory.getLogger(SdnUtils.class);

    /** Do not instantiate. */
    private SdnUtils() { }

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
            Entities.manage(network);
            Entities.start(network, provider.getLocations());
            Entities.waitForServiceUp(network);
        } else {
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
            network = (VirtualNetwork) Iterables.find(provider.sensors().get(SdnProvider.SDN_NETWORKS).getMembers(),
                    EntityPredicates.attributeEqualTo(VirtualNetwork.NETWORK_ID, networkId));
        }

        // Rescan SDN network groups for containers
        DynamicGroup group = (DynamicGroup) Iterables.find(provider.sensors().get(SdnProvider.SDN_APPLICATIONS).getMembers(),
                EntityPredicates.attributeEqualTo(VirtualNetwork.NETWORK_ID, networkId));
        group.rescanEntities();

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

}
