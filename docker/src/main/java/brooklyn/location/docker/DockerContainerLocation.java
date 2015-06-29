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
package brooklyn.location.docker;

import static brooklyn.util.GroovyJavaMethods.truth;
import static brooklyn.util.ssh.BashCommands.sudo;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.container.DockerCallbacks;
import brooklyn.entity.container.DockerUtils;
import brooklyn.entity.container.docker.DockerContainer;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.location.Location;
import brooklyn.location.PortRange;
import brooklyn.location.access.PortForwardManager;
import brooklyn.location.basic.HasSubnetHostname;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.basic.SupportsPortForwarding;
import brooklyn.location.dynamic.DynamicLocation;
import brooklyn.location.jclouds.JcloudsSshMachineLocation;
import brooklyn.location.jclouds.JcloudsUtil;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.internal.ssh.ShellTool;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.net.Cidr;
import brooklyn.util.net.Protocol;
import brooklyn.util.pool.Pool;
import brooklyn.util.ssh.IptablesCommands;
import brooklyn.util.ssh.IptablesCommands.Chain;
import brooklyn.util.ssh.IptablesCommands.Policy;
import brooklyn.util.time.Duration;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.base.Predicates;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.net.HostAndPort;

/**
 * A {@link Location} that wraps a Docker container.
 * <p>
 * The underlying container is presented as an {@link SshMachineLocation} obtained using the jclouds Docker driver.
 */
public class DockerContainerLocation extends SshMachineLocation implements SupportsPortForwarding, HasSubnetHostname, DynamicLocation<DockerContainer, DockerContainerLocation> {

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
        if (getOwner().config().get(DockerHost.OPEN_IPTABLES)) {
            SshMachineLocation host = getOwner().getDockerHost().getDynamicLocation().getMachine();
            LOG.debug("Using iptables to add access for TCP/{} to {}", port, host);
            List<String> commands = ImmutableList.of(
                    sudo("iptables -L INPUT -nv | grep -q 'tcp dpt:"+port+"'"),
                    format("if [ $? -eq 0 ]; then ( %s ); else ( %s ); fi",
                            sudo("iptables -C INPUT -s 0/0 -p tcp --dport "+port+" -j ACCEPT"),
                            IptablesCommands.insertIptablesRule(Chain.INPUT, Protocol.TCP, port, Policy.ACCEPT)));
            int result = host.execCommands(format("Open iptables TCP/%d", port), commands);
            if (result != 0) {
                String msg = format("Error running iptables update for TCP/%d on %s", port, host);
                LOG.error(msg);
                throw new RuntimeException(msg);
            }
        }
    }

    public int getMappedPort(int portNumber) {
        String containerId = getOwner().getContainerId();
        Map<Integer, Integer> mapping = JcloudsUtil.dockerPortMappingsFor(getOwner().getDockerHost().getJcloudsLocation(), containerId);
        Integer publicPort = mapping.get(portNumber);
        if (publicPort == null) {
            LOG.warn("Unable to map port {} for Container {}. Mappings: {}",
                    new Object[]{portNumber, containerId, Joiner.on(", ").withKeyValueSeparator("=").join(mapping)});
            publicPort = -1;
        } else {
            LOG.debug("Docker mapped port {} to {} for Container {}", new Object[]{portNumber, publicPort, containerId});
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
        String dockerHost = getAddress().getHostAddress();
        PortForwardManager portForwardManager = getOwner().getDockerHost().getSubnetTier().getPortForwardManager();
        portForwardManager.recordPublicIpHostname(dockerHost, dockerHost);
        portForwardManager.acquirePublicPortExplicit(dockerHost, hostPort);
        portForwardManager.associate(dockerHost, hostPort, this, containerPort);
        //FIXME portForwardManager.associate(dockerHost, HostAndPort.fromParts(dockerHost, hostPort), this, containerPort);
    }

    @Override
    public HostAndPort getSocketEndpointFor(Cidr accessor, int privatePort) {
        String dockerHost = getAddress().getHostAddress();
        int hostPort = getMappedPort(privatePort);
        return HostAndPort.fromParts(dockerHost, hostPort);
    }

    @Override
    public int execScript(Map<String,?> props, String summaryForLogging, List<String> commands, Map<String,?> env) {
        Iterable<String> filtered = Iterables.filter(commands, DockerCallbacks.FILTER);
        for (String commandString : filtered) {
            parseDockerCallback(commandString);
        }
        return super.execScript(props, summaryForLogging, commands, env);
    }

    @Override
    public int execCommands(Map<String,?> props, String summaryForLogging, List<String> commands, Map<String,?> env) {
        Iterable<String> filtered = Iterables.filter(commands, DockerCallbacks.FILTER);
        for (String commandString : filtered) {
            parseDockerCallback(commandString);
        }
        return super.execCommands(props, summaryForLogging, commands, env);
    }

    private void parseDockerCallback(String commandString) {
        List<String> tokens = DockerCallbacks.PARSER.splitToList(commandString);
        int callback = Iterables.indexOf(tokens, Predicates.equalTo(DockerCallbacks.DOCKER_HOST_CALLBACK));
        if (callback == -1) {
            LOG.warn("Could not find callback token: {}", commandString);
            throw new IllegalStateException("Cannot find callback token in command line");
        }
        String command = tokens.get(callback + 1);
        LOG.info("Executing callback for {}: {}", getOwner(), command);
        if (DockerCallbacks.COMMIT.equalsIgnoreCase(command)) {
            String containerId = getOwner().getContainerId();
            String imageName = getOwner().getAttribute(DockerContainer.IMAGE_NAME);
            String output = getOwner().getDockerHost().runDockerCommandTimeout(
                    format("commit %s %s", containerId, imageName),
                    Duration.minutes(20));
            String imageId = DockerUtils.checkId(output);
            ((EntityLocal) getOwner().getRunningEntity()).setAttribute(DockerContainer.IMAGE_ID, imageId);
            ((EntityLocal) getOwner()).setAttribute(DockerContainer.IMAGE_ID, imageId);
            getOwner().getDockerHost().getDynamicLocation().markImage(imageName);
        } else if (DockerCallbacks.PUSH.equalsIgnoreCase(command)) {
            String imageName = getOwner().getAttribute(DockerContainer.IMAGE_NAME);
            getOwner().getDockerHost().runDockerCommand(format("push %s", imageName));
        } else if (DockerCallbacks.SUBNET_ADDRESS.equalsIgnoreCase(command)) {
            String address = getOwner().getAttribute(Attributes.SUBNET_ADDRESS);
            ((EntityLocal) getOwner().getRunningEntity()).setAttribute(Attributes.SUBNET_ADDRESS, address);
            ((EntityLocal) getOwner().getRunningEntity()).setAttribute(Attributes.SUBNET_HOSTNAME, address);
        } else {
            LOG.warn("Unknown Docker host command: {}", command);
        }
    }

    @Override
    public void releasePort(int portNumber) {
        machine.releasePort(portNumber);
    }

    @Override
    public InetAddress getAddress() {
        return getOwner().getDockerHost().getDynamicLocation().getMachine().getAddress();
    }

    @Override
    public void close() throws IOException {
        LOG.debug("Close called on Docker container {}: {}", machine, this);
        try {
            machine.close();
            if (dockerContainer.getAttribute(DockerContainer.SERVICE_UP)) {
                LOG.info("Stopping Docker container entity for {}: {}", this, dockerContainer);
                dockerContainer.stop();
            }
            LOG.info("Docker container closed: {}", this);
        } catch (Exception e) {
            LOG.warn("Error closing Docker container {}: {}", this, e.getMessage());
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public ToStringHelper string() {
        return super.string()
                .add("entity", entity)
                .add("machine", machine)
                .add("owner", dockerContainer);
    }

    @Override
    public String getSubnetHostname() {
        return getSubnetIp();
    }

    @Override
    public String getSubnetIp() {
        String containerId = checkNotNull(getOwner().getContainerId(), "containerId");
        String containerAddress = getOwner().getDockerHost().runDockerCommand(
                "inspect --format={{.NetworkSettings.IPAddress}} " + containerId);
        return containerAddress.trim();
    }

}
