/*
 * Copyright 2013-2015 by Cloudsoft Corporation Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.entity.mesos.framework.marathon;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.HostAndPort;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.PortRange;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.location.access.PortForwardManager;
import org.apache.brooklyn.core.location.access.PortForwardManagerImpl;
import org.apache.brooklyn.core.location.access.PortMapping;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.net.Cidr;
import org.apache.brooklyn.util.net.HasNetworkAddresses;
import org.apache.brooklyn.util.net.Protocol;

import brooklyn.location.mesos.framework.marathon.MarathonTaskLocation;
import brooklyn.networking.common.subnet.PortForwarder;

public class MarathonPortForwarder implements PortForwarder {

    private static final Logger LOG = LoggerFactory.getLogger(MarathonPortForwarder.class);

    private PortForwardManager portForwardManager;
    private String marathonHostname;
    private Map<HostAndPort, HostAndPort> portmap;

    public MarathonPortForwarder() {
    }

    public MarathonPortForwarder(PortForwardManager portForwardManager) {
        this.portForwardManager = portForwardManager;
    }

    @Override
    public void injectManagementContext(ManagementContext managementContext) {
        if (portForwardManager == null) {
            portForwardManager = (PortForwardManager) managementContext.getLocationRegistry().resolve("portForwardManager(scope=global)");
        }
    }

    public void init(String marathonHostIp) {
        this.marathonHostname = marathonHostIp;
        this.portmap = MutableMap.of();
    }

    @Override
    public void inject(Entity owner, List<Location> locations) {
        // no-op
    }

    @Override
    public PortForwardManager getPortForwardManager() {
        if (portForwardManager == null) {
            LOG.warn("Instantiating new PortForwardManager, because ManagementContext not injected into "+this
                    +" (deprecated behaviour that will not be supported in future versions)");
            portForwardManager = new PortForwardManagerImpl();
        }
        return portForwardManager;
    }

    @Override
    public String openGateway() {
        return marathonHostname;
    }

    @Override
    public String openStaticNat(Entity serviceToOpen) {
        throw new UnsupportedOperationException("Can only open individual ports; not static nat with iptables");
    }

    @Override
    public void openFirewallPort(Entity entity, int port, Protocol protocol, Cidr accessingCidr) {
        LOG.debug("no-op in {} for openFirewallPort({}, {}, {}, {})", new Object[] { this, entity, port, protocol, accessingCidr });
    }

    @Override
    public void openFirewallPortRange(Entity entity, PortRange portRange, Protocol protocol, Cidr accessingCidr) {
        LOG.debug("no-op in {} for openFirewallPortRange({}, {}, {}, {})", new Object[] { this, entity, portRange, protocol, accessingCidr });
    }

    @Override
    public HostAndPort openPortForwarding(HasNetworkAddresses targetMachine, int targetPort, Optional<Integer> optionalPublicPort,
            Protocol protocol, Cidr accessingCidr) {
        if (targetMachine instanceof MarathonTaskLocation) {
            PortForwardManager pfw = getPortForwardManager();
            HostAndPort publicSide;
            if (optionalPublicPort.isPresent()) {
                int publicPort = optionalPublicPort.get();
                publicSide = HostAndPort.fromParts(marathonHostname, publicPort);
            } else {
                publicSide = HostAndPort.fromParts(marathonHostname, targetPort);
            }
            pfw.associate(marathonHostname, publicSide, (MarathonTaskLocation) targetMachine, targetPort);
            return publicSide;
        } else {
            throw new IllegalArgumentException("Cannot forward ports for a non-Marathon target: " + targetMachine);
        }
    }

    @Override
    public HostAndPort openPortForwarding(HostAndPort targetSide, Optional<Integer> optionalPublicPort, Protocol protocol, Cidr accessingCidr) {
        PortForwardManager pfw = getPortForwardManager();
        HostAndPort publicSide;
        if (optionalPublicPort.isPresent()) {
            int publicPort = optionalPublicPort.get();
            publicSide = HostAndPort.fromParts(marathonHostname, publicPort);
        } else {
            publicSide = HostAndPort.fromParts(marathonHostname, targetSide.getPort());
        }
        pfw.associate(marathonHostname, publicSide, targetSide.getPort());
        portmap.put(publicSide, targetSide);
        return publicSide;
    }

    @Override
    public boolean closePortForwarding(HostAndPort targetSide, HostAndPort publicSide, Protocol protocol) {
        portmap.remove(publicSide);
        return false;
    }

    @Override
    public boolean closePortForwarding(HasNetworkAddresses machine, int targetPort, HostAndPort publicSide, Protocol protocol) {
        portmap.remove(publicSide);
        return false;
    }

    @Override
    public boolean isClient() {
        return false;
    }

    public Map<HostAndPort, HostAndPort> getPortMapping() {
        return ImmutableMap.copyOf(portmap);
    }

}
