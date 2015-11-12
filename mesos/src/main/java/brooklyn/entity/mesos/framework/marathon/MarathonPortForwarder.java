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

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.net.HostAndPort;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.api.location.PortRange;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.location.access.PortForwardManager;
import org.apache.brooklyn.core.location.access.PortForwardManagerImpl;
import org.apache.brooklyn.core.location.access.PortMapping;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.net.Cidr;
import org.apache.brooklyn.util.net.HasNetworkAddresses;
import org.apache.brooklyn.util.net.Protocol;

import brooklyn.networking.common.subnet.PortForwarder;

public class MarathonPortForwarder implements PortForwarder {

    private static final Logger LOG = LoggerFactory.getLogger(MarathonPortForwarder.class);

    private PortForwardManager portForwardManager;
    private String marathonEndpoint;
    private String marathonHostname;
    private String marathonIdentity;
    private String marathonCredential;

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
    
    public void init(String marathonHostIp, int marathonHostPort, String username, String password) {
        this.marathonEndpoint = URI.create("http://" + marathonHostIp + ":" + marathonHostPort).toASCIIString();
        this.marathonHostname = marathonHostIp;
        this.marathonIdentity = username;
        this.marathonCredential = password;
    }


    public void init(URI endpoint, String identity, String credential) {
        this.marathonEndpoint = endpoint.toASCIIString();
        this.marathonHostname = endpoint.getHost();
        this.marathonIdentity = identity;
        this.marathonCredential = credential;
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
        // IP of port-forwarder already exists
        return marathonHostname;
    }

    @Override
    public String openStaticNat(Entity serviceToOpen) {
        throw new UnsupportedOperationException("Can only open individual ports; not static nat with iptables");
    }

    @Override
    public void openFirewallPort(Entity entity, int port, Protocol protocol, Cidr accessingCidr) {
        // TODO If port is already open in marathon port-mapping then no-op; otherwise UnsupportedOperationException currently
        LOG.debug("no-op in {} for openFirewallPort({}, {}, {}, {})", new Object[] {this, entity, port, protocol, accessingCidr});
    }

    @Override
    public void openFirewallPortRange(Entity entity, PortRange portRange, Protocol protocol, Cidr accessingCidr) {
        // TODO If port is already open in marathon port-mapping then no-op; otherwise UnsupportedOperationException currently
        LOG.debug("no-op in {} for openFirewallPortRange({}, {}, {}, {})", new Object[] {this, entity, portRange, protocol, accessingCidr});
    }

    @Override
    public HostAndPort openPortForwarding(HasNetworkAddresses targetMachine, int targetPort, Optional<Integer> optionalPublicPort,
            Protocol protocol, Cidr accessingCidr) {

        String targetIp = Iterables.getFirst(Iterables.concat(targetMachine.getPrivateAddresses(), targetMachine.getPublicAddresses()), null);
        if (targetIp==null) {
            throw new IllegalStateException("Failed to open port-forwarding for machine "+targetMachine+" because its" +
                    " location has no target ip: "+targetMachine);
        }
        HostAndPort targetSide = HostAndPort.fromParts(targetIp, targetPort);
        HostAndPort newFrontEndpoint = openPortForwarding(targetSide, optionalPublicPort, protocol, accessingCidr);
        LOG.debug("Enabled port-forwarding for {} port {} (VM {}), via {}", new Object[] {targetMachine, targetPort, targetMachine, newFrontEndpoint});
        return newFrontEndpoint;
    }

    @Override
    public HostAndPort openPortForwarding(HostAndPort targetSide, Optional<Integer> optionalPublicPort, Protocol protocol, Cidr accessingCidr) {
        // FIXME Does this actually open the port forwarding? Or just record that the port is supposed to be open?
        PortForwardManager pfw = getPortForwardManager();
        PortMapping mapping;
        if (optionalPublicPort.isPresent()) {
            int publicPort = optionalPublicPort.get();
            mapping = pfw.acquirePublicPortExplicit(marathonHostname, publicPort);
        } else {
            mapping = pfw.acquirePublicPortExplicit(marathonHostname, targetSide.getPort());
        }
        if (mapping == null) {
            return HostAndPort.fromParts(marathonHostname, targetSide.getPort());
        } else {
            return HostAndPort.fromParts(marathonHostname, mapping.getPublicPort());
        }
    }

    @Override
    public boolean closePortForwarding(HostAndPort targetSide, HostAndPort publicSide, Protocol protocol) {
        // no-op; we leave the port-forwarding in place.
        // This is symmetrical with openPortForwarding, which doesn't actually open it - that just returns the existing open mapping.
        return false;
    }

    @Override
    public boolean closePortForwarding(HasNetworkAddresses machine, int targetPort, HostAndPort publicSide, Protocol protocol) {
        // no-op; we leave the port-forwarding in place.
        // This is symmetrical with openPortForwarding, which doesn't actually open it - that just returns the existing open mapping.
        return false;
    }

    public Map<Integer, Integer> getPortMappings(MachineLocation targetMachine) {
        Map<Integer, String> tcpPortMappings = targetMachine.config().get(SshMachineLocation.TCP_PORT_MAPPINGS);
        Map<Integer, Integer> portMappings = Maps.newLinkedHashMap();
//        for (Integer containerPort : tcpPortMappings) {
//            HostAndPort 
//        }
        return portMappings;
    }

    @Override
    public boolean isClient() {
        return false;
    }
}
