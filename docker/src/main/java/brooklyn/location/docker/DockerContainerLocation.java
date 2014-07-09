/*
 * Copyright 2014 by Cloudsoft Corporation Limited
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
package brooklyn.location.docker;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HostAndPort;
import com.google.common.net.InetAddresses;

import brooklyn.entity.Entity;
import brooklyn.entity.container.docker.DockerContainer;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.location.Location;
import brooklyn.location.PortRange;
import brooklyn.location.access.PortForwardManager;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.basic.SupportsPortForwarding;
import brooklyn.location.dynamic.DynamicLocation;
import brooklyn.location.jclouds.JcloudsSshMachineLocation;
import brooklyn.location.jclouds.JcloudsUtil;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.net.Cidr;
import brooklyn.util.net.Protocol;
import brooklyn.util.ssh.IptablesCommands;
import brooklyn.util.ssh.IptablesCommands.Chain;
import brooklyn.util.ssh.IptablesCommands.Policy;

/**
 * A {@link Location} that wraps a Docker container.
 * <p>
 * The underlying container is presented as an {@link SshMachineLocation} obtained using the jclouds Docker driver.
 */
public class DockerContainerLocation extends SshMachineLocation implements SupportsPortForwarding, DynamicLocation<DockerContainer, DockerContainerLocation> {

    /** serialVersionUID */
    private static final long serialVersionUID = 610389734596906782L;

    private static final Logger LOG = LoggerFactory.getLogger(DockerContainerLocation.class);

    @SetFromFlag("entity")
    private Entity entity;

    @SetFromFlag("machine")
    private JcloudsSshMachineLocation machine;

    @SetFromFlag("owner")
    private DockerContainer dockerContainer;

    @Override
    public void init() {
        super.init();
    }

    @Override
    public DockerContainer getOwner() {
        return dockerContainer;
    }

    public JcloudsSshMachineLocation getMachine() {
        return machine;
    }

    /*
     * Delegate port operations to machine. Note that firewall configuration is
     * fixed after initial provisioning, so updates use iptables to open ports.
     */

    private void addIptablesRule(Integer port) {
        if (getOwner().getConfig(DockerInfrastructure.OPEN_IPTABLES)) {
            SshMachineLocation host = getOwner().getDockerHost().getDynamicLocation().getMachine();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Using iptables to add access for TCP/{} to {}", port, host);
            }
            List<String> commands = ImmutableList.of(
                    IptablesCommands.insertIptablesRule(Chain.INPUT, Protocol.TCP, port, Policy.ACCEPT),
                    IptablesCommands.saveIptablesRules());
            int result = host.execCommands(String.format("Open iptables TCP/%d", port), commands);
            /*
            if (result != 0) {
                String msg = String.format("Error running iptables update for TCP/%d on %s", port, host);
                LOG.error(msg);
                throw new RuntimeException(msg);
            }
            */
        }
    }

    public int getMappedPort(int portNumber) {
        String containerId = getOwner().getContainerId();
        Map<Integer, Integer> mapping = JcloudsUtil.dockerPortMappingsFor(getOwner().getDockerHost().getJcloudsLocation(), containerId);
        Integer publicPort = mapping.get(portNumber);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Docker mapped port {} to {} for Container {}", new Object[] { portNumber, publicPort, containerId });
        }
        return publicPort;
    }

    @Override
    public boolean obtainSpecificPort(int portNumber) {
        boolean result = machine.obtainSpecificPort(portNumber);
        if (result) {
            int targetPort = getMappedPort(portNumber);
            mapPort(targetPort, portNumber);
            addIptablesRule(targetPort);
        }
        return result;
    }

    @Override
    public int obtainPort(PortRange range) {
        int portNumber = machine.obtainPort(range);
        int targetPort = getMappedPort(portNumber);
        mapPort(targetPort, portNumber);
        addIptablesRule(targetPort);
        return portNumber;
    }

    private void mapPort(int hostPort, int containerPort) {
        String dockerHost = getOwner().getDockerHost().getDynamicLocation().getMachine().getAddress().getHostAddress();
        PortForwardManager portForwardManager = getOwner().getDockerHost().getSubnetTier().getPortForwardManager();
        portForwardManager.recordPublicIpHostname(dockerHost, dockerHost);
        portForwardManager.acquirePublicPortExplicit(dockerHost, hostPort);
        portForwardManager.associate(dockerHost, hostPort, this, containerPort);
    }

    @Override
    public HostAndPort getSocketEndpointFor(Cidr accessor, int privatePort) {
        String dockerHost = getOwner().getDockerHost().getDynamicLocation().getMachine().getAddress().getHostAddress();
        int hostPort = getMappedPort(privatePort);
        return HostAndPort.fromParts(dockerHost, hostPort);
    }

    @Override
    public void releasePort(int portNumber) {
        machine.releasePort(portNumber);
    }

    @Override
    public InetAddress getAddress() {
        String containerAddress = getOwner().getDockerHost().runDockerCommand("inspect --format={{.NetworkSettings.IPAddress}} " + getOwner().getContainerId());
        return InetAddresses.forString(containerAddress.trim());
    }

    @Override
    public void close() throws IOException {
        machine.close();
        LOG.info("Close called on Docker container location: {}", this);
    }

    @Override
    public ToStringHelper string() {
        return super.string()
                .add("entity", entity)
                .add("machine", machine)
                .add("owner", dockerContainer);
    }

}
