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
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.render.RendererHints;
import brooklyn.entity.basic.BasicStartableImpl;
import brooklyn.entity.basic.DelegateEntity;
import brooklyn.location.Location;
import brooklyn.networking.location.NetworkProvisioningExtension;
import brooklyn.networking.sdn.SdnProvider;
import brooklyn.util.net.Cidr;
import brooklyn.util.text.StringFunctions;
import brooklyn.util.text.Strings;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public class VirtualNetworkImpl extends BasicStartableImpl implements VirtualNetwork {

    private static final Logger LOG = LoggerFactory.getLogger(SdnProvider.class);

    @Override
    public void init() {
        LOG.info("Starting virtual network segment id {}", getId());
        super.init();
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        super.start(locations);

        Optional<Location> found = Iterables.tryFind(getLocations(), new Predicate<Location>() {
            @Override
            public boolean apply(Location input) {
                return input.hasExtension(NetworkProvisioningExtension.class);
            }
        });
        if (!found.isPresent()) {
            throw new IllegalStateException("Cannot start a virtual network in any location: " + Iterables.toString(getLocations()));
        }
        NetworkProvisioningExtension<ManagedNetwork> provisioner = found.get().getExtension(NetworkProvisioningExtension.class);

        String networkId = getConfig(NETWORK_NAME);
        Cidr cidr = getConfig(NETWORK_CIDR);
        Map<String, Object> flags = getConfig(NETWORK_PROVISIONING_FLAGS);
        if (Strings.isBlank(networkId)) networkId = getId(); // Use our Entity ID for unique name

        ManagedNetwork network = provisioner.addNetwork(networkId, cidr, flags);
        setAttribute(MANAGED_NETWORK, network);
    }

    @Override
    public ManagedNetwork getManagedNetwork() { return getAttribute(MANAGED_NETWORK); }

    static {
        RendererHints.register(MANAGED_NETWORK, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(Cidr.class, RendererHints.displayValue(StringFunctions.toStringFunction()));
    }
}
