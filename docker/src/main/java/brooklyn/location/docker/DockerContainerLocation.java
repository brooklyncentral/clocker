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
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import brooklyn.entity.container.docker.DockerContainer;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.location.Location;
import brooklyn.location.PortRange;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.dynamic.DynamicLocation;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.net.Protocol;
import brooklyn.util.ssh.IptablesCommands;
import brooklyn.util.ssh.IptablesCommands.Chain;
import brooklyn.util.ssh.IptablesCommands.Policy;

/**
 * A {@link Location} that wraps a Docker container.
 * <p>
 * The underlying container is presented as an {@link SshMachineLocation} obtained using the jclouds Docker driver.
 */
public class DockerContainerLocation extends SshMachineLocation implements DynamicLocation<DockerContainer, DockerContainerLocation> {

    private static final Logger LOG = LoggerFactory.getLogger(DockerContainerLocation.class);

    @SetFromFlag("machine")
    private SshMachineLocation machine;

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

    public SshMachineLocation getMachine() {
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
                    IptablesCommands.cleanUpIptablesRules(),
                    IptablesCommands.saveIptablesRules());
            int result = host.execCommands(String.format("Open iptables TCP/%d", port), commands);
            if (result != 0) {
                String msg = String.format("Error running iptables update for TCP/%d on %s", port, host);
                LOG.error(msg);
                throw new RuntimeException(msg);
            }
        }
    }

    public int getMappedPort(int portNumber) {
        String portMap = getOwner().getDockerHost()
                .runDockerCommand("inspect -f {{.NetworkSettings.Ports}} " + getOwner().getContainerId());
        int i = portMap.indexOf(portNumber + "/tcp:[");
        if (i == -1) throw new IllegalStateException();
        int j = portMap.substring(i).indexOf("HostPort:");
        int k = portMap.substring(i + j).indexOf("]]");
        String hostPort = portMap.substring(i + j + "HostPort:".length(), i + j + k);
        return Integer.valueOf(hostPort);
    }

    @Override
    public boolean obtainSpecificPort(int portNumber) {
        SshMachineLocation host = getOwner().getDockerHost().getDynamicLocation().getMachine();
        boolean result = host.obtainSpecificPort(portNumber);
        if (result) {
            int targetPort = getMappedPort(portNumber);
            getOwner().getDockerHost().getPortForwarder()
                    .openPortForwarding(machine, portNumber, Optional.of(targetPort), Protocol.TCP, null);
            addIptablesRule(targetPort);
        }
        return result;
    }

    @Override
    public int obtainPort(PortRange range) {
        SshMachineLocation host = getOwner().getDockerHost().getDynamicLocation().getMachine();
        int portNumber = host.obtainPort(range);
        int targetPort = getMappedPort(portNumber);
        //getOwner().getDockerHost().getPortForwarder().openPortForwarding(machine, portNumber, Optional.of(targetPort), Protocol.TCP, null);
        //addIptablesRule(targetPort);
        return portNumber;
    }

    @Override
    public void releasePort(int portNumber) {
        SshMachineLocation host = getOwner().getDockerHost().getDynamicLocation().getMachine();
        host.releasePort(portNumber);
    }

    @Override
    public void close() throws IOException {
        machine.close();
        LOG.info("Close called on Docker container location: {}", this);
    }

    @Override
    public ToStringHelper string() {
        return super.string()
                .add("machine", machine)
                .add("dockerContainer", dockerContainer);
    }

}
