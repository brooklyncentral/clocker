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
package brooklyn.networking;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.Location;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.networking.location.NetworkProvisioningExtension;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public class OpenStackVirtualNetworkImpl extends VirtualNetworkImpl implements OpenStackVirtualNetwork {

    private static final Logger LOG = LoggerFactory.getLogger(VirtualNetwork.class);

    @Override
    public void init() {
        LOG.info("Starting openstack network segment id {}", getId());
        super.init();
    }

    public NetworkProvisioningExtension findNetworkProvisioner(Collection<? extends Location> locations) {
        Optional<? extends Location> found = Iterables.tryFind(locations, new Predicate<Location>() {
            @Override
            public boolean apply(Location input) {
                return input instanceof JcloudsLocation
                        && ((JcloudsLocation) input).getProvider().startsWith("openstack");
            }
        });
        if (!found.isPresent()) {
            throw new IllegalStateException("Cannot find openstack location: " + Iterables.toString(getLocations()));
        }
        JcloudsLocation provisioner = (JcloudsLocation) found.get();
        NetworkProvisioningExtension extension = new OpenStackNetworkProvisioner(provisioner);
        return extension;
    }

}
