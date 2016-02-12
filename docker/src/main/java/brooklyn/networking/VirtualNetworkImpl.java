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

import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.feed.ConfigToAttributes;
import org.apache.brooklyn.core.location.Locations;
import org.apache.brooklyn.entity.stock.BasicStartableImpl;
import org.apache.brooklyn.entity.stock.DelegateEntity;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.net.Cidr;
import org.apache.brooklyn.util.text.StringFunctions;
import org.apache.brooklyn.util.text.Strings;

import brooklyn.networking.location.NetworkProvisioningExtension;

public class VirtualNetworkImpl extends BasicStartableImpl implements VirtualNetwork {

    private static final Logger LOG = LoggerFactory.getLogger(VirtualNetwork.class);

    @Override
    public void init() {
        LOG.info("Starting virtual network segment id {}", getId());
        super.init();

        String networkId = config().get(NETWORK_ID);
        if (Strings.isEmpty(networkId)) networkId = getId();

        sensors().set(NETWORK_ID, networkId);
        setDisplayName(String.format("Virtual Network (%s)", networkId));
        ConfigToAttributes.apply(this, SDN_PROVIDER);
    }

    @Override
    public void start(Collection<? extends Location> locs) {
        addLocations(locs);
        List<Location> locations = MutableList.copyOf(Locations.getLocationsCheckingAncestors(locs, this));

        sensors().set(SERVICE_UP, Boolean.FALSE);

        super.start(locations);

        try {
            NetworkProvisioningExtension provisioner = null;
            Entity sdn = sensors().get(SDN_PROVIDER);
            if (sdn instanceof NetworkProvisioningExtension) {
                provisioner = (NetworkProvisioningExtension) sdn;
            } else {
                provisioner = findNetworkProvisioner(locations);
            }
            sensors().set(NETWORK_PROVISIONER, provisioner);
            provisioner.provisionNetwork(this);
        } catch (Exception e) {
            ServiceStateLogic.setExpectedState(this, Lifecycle.ON_FIRE);
            throw Exceptions.propagate(e);
        }

        sensors().set(SERVICE_UP, Boolean.TRUE);
    }

    public NetworkProvisioningExtension findNetworkProvisioner(Collection<? extends Location> locations) {
        Optional<? extends Location> found = Iterables.tryFind(locations, new Predicate<Location>() {
            @Override
            public boolean apply(Location input) {
                return input.hasExtension(NetworkProvisioningExtension.class);
            }
        });
        if (!found.isPresent()) {
            throw new IllegalStateException("Cannot start a virtual network in any location: " + Iterables.toString(getLocations()));
        }
        NetworkProvisioningExtension provisioner = found.get().getExtension(NetworkProvisioningExtension.class);
        return provisioner;
    }

    @Override
    public void stop() {
        sensors().set(SERVICE_UP, Boolean.FALSE);

        NetworkProvisioningExtension provisioner = sensors().get(NETWORK_PROVISIONER);
        provisioner.deallocateNetwork(this);

        super.stop();
    }

    static {
        RendererHints.register(Cidr.class, RendererHints.displayValue(StringFunctions.toStringFunction()));

        RendererHints.register(NETWORKED_APPLICATIONS, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
    }
}
