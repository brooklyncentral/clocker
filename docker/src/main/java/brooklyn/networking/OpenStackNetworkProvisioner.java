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
package brooklyn.networking;

import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jclouds.compute.domain.SecurityGroup;
import org.jclouds.compute.extensions.SecurityGroupExtension;
import org.jclouds.domain.Location;
import org.jclouds.net.domain.IpPermission;
import org.jclouds.net.domain.IpProtocol;

import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.util.net.Cidr;

import brooklyn.networking.location.NetworkProvisioningExtension;

public class OpenStackNetworkProvisioner implements NetworkProvisioningExtension {

    private static final Logger LOG = LoggerFactory.getLogger(OpenStackNetworkProvisioner.class);

    private final JcloudsLocation location;

    public OpenStackNetworkProvisioner(JcloudsLocation location) {
        this.location = location;
    }

    @Override
    public void provisionNetwork(VirtualNetwork network) {
        String name = network.config().get(VirtualNetwork.NETWORK_ID);
        SecurityGroupExtension extension = location.getComputeService().getSecurityGroupExtension().get();
        Set<SecurityGroup> groups = extension.listSecurityGroups();
        String id = null;

        // Look for existing security group with the desired name
        for (SecurityGroup each : groups) {
            if (each.getName().equalsIgnoreCase(name)) {
               id = each.getId();
               break;
            }
        }

        // If not found then create a new group
        if (id == null) {
            Location region = location.getComputeService().listAssignableLocations().iterator().next();
            SecurityGroup added = extension.createSecurityGroup(name, region);
            id = added.getId();
            IpPermission rules = IpPermission.builder()
                    .cidrBlock(network.config().get(VirtualNetwork.NETWORK_CIDR).toString())
                    .ipProtocol(IpProtocol.TCP)
                    .fromPort(1)
                    .toPort(65535)
                    .build();
            extension.addIpPermission(rules, added);
            LOG.info("Added new security group {} with ID {}: {}", new Object[] { added.getName(), id, rules.toString() });
        }

        // Use the OpenStack UUID as the virtual network id
        network.sensors().set(VirtualNetwork.NETWORK_ID, id);
    }

    @Override
    public Map<String, Cidr> listManagedNetworkAddressSpace() {
        // TODO return the managed CIDRs from OpenStack?
        return null;
    }

    @Override
    public void deallocateNetwork(VirtualNetwork network) {
        // TODO determine whether it is safe to delete the security group?
    }

}
