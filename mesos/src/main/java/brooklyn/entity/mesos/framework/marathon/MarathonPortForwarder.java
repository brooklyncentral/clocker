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
package brooklyn.entity.mesos.framework.marathon;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.HostAndPort;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.PortRange;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.location.Machines;
import org.apache.brooklyn.core.location.access.PortForwardManager;
import org.apache.brooklyn.core.location.access.PortForwardManagerImpl;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.internal.ssh.SshTool;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.net.Cidr;
import org.apache.brooklyn.util.net.HasNetworkAddresses;
import org.apache.brooklyn.util.net.Protocol;
import org.apache.brooklyn.util.ssh.BashCommands;

import brooklyn.entity.mesos.MesosCluster;
import brooklyn.entity.mesos.MesosSlave;
import brooklyn.location.mesos.framework.marathon.MarathonTaskLocation;
import brooklyn.networking.common.subnet.PortForwarder;
import brooklyn.networking.sdn.mesos.CalicoModule;

public class MarathonPortForwarder implements PortForwarder {

    private static final Logger LOG = LoggerFactory.getLogger(MarathonPortForwarder.class);

    private PortForwardManager portForwardManager;
    private String marathonHostname;
    private Map<HostAndPort, HostAndPort> portmap;
    private MesosCluster cluster;
    private MesosSlave slave;
    private SshMachineLocation host;

    public MarathonPortForwarder() {
    }

    public MarathonPortForwarder(PortForwardManager portForwardManager) {
        this.portForwardManager = portForwardManager;
    }

    @Override
    public void setManagementContext(ManagementContext managementContext) {
        if (portForwardManager == null) {
            portForwardManager = (PortForwardManager) managementContext.getLocationRegistry().resolve("portForwardManager(scope=global)");
        }
    }

    public void init(String marathonHostIp, MesosCluster cluster) {
        this.marathonHostname = marathonHostIp;
        this.cluster = cluster;
        this.portmap = MutableMap.of();
        this.slave = cluster.getMesosSlave(marathonHostname);
        Maybe<SshMachineLocation> machine = Machines.findUniqueSshMachineLocation(slave.getLocations());
        this.host = machine.get();
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
        LOG.debug("Open iptables rule for {}, {}, {}, {}", new Object[] { this, entity, port, protocol, accessingCidr });
        if (cluster.config().get(MesosCluster.SDN_ENABLE)) {
            HostAndPort target = portmap.get(HostAndPort.fromParts(marathonHostname, port));
            addIptablesRule(port, target);
            String profile = entity.getApplicationId(); // TODO allow other Calico profiles
            String command = BashCommands.sudo(String.format("calicoctl profile %s rule add inbound allow tcp to ports %d", profile, target.getPort()));
            CalicoModule calico = (CalicoModule) cluster.sensors().get(MesosCluster.SDN_PROVIDER);
            calico.execCalicoCommand(slave, command);
        }
    }

    @Override
    public void openFirewallPortRange(Entity entity, PortRange portRange, Protocol protocol, Cidr accessingCidr) {
        LOG.debug("no-op in {} for openFirewallPortRange({}, {}, {}, {})", new Object[] { this, entity, portRange, protocol, accessingCidr });
    }

    private void addIptablesRule(Integer hostPort, HostAndPort container) {
        LOG.debug("Using iptables to add access for TCP/{} to {}", hostPort, host);
        List<String> commands = ImmutableList.of(
                        BashCommands.sudo(String.format("iptables -t nat -A PREROUTING -p tcp --dport %d -j DNAT --to-destination %s", hostPort, container.toString())),
                        BashCommands.sudo(String.format("iptables -A FORWARD -p tcp -d %s --dport %d -m state --state NEW,ESTABLISHED,RELATED -j ACCEPT", container.getHostText(), container.getPort())));
        int result = host.execCommands(MutableMap.of(SshTool.PROP_ALLOCATE_PTY.getName(), true), String.format("Open iptables TCP/%d", hostPort), commands);
        if (result != 0) {
            String msg = String.format("Error running iptables update for TCP/%d on %s", hostPort, host);
            LOG.error(msg);
            throw new RuntimeException(msg);
        }
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
