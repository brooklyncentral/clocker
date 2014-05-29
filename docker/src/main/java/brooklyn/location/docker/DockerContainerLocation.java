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
import java.util.Map;

import org.jclouds.ContextBuilder;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.docker.DockerApi;
import org.jclouds.docker.domain.Container;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.container.docker.DockerContainer;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.location.Location;
import brooklyn.location.PortRange;
import brooklyn.location.access.PortForwardManager;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.basic.SupportsPortForwarding;
import brooklyn.location.dynamic.DynamicLocation;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsSshMachineLocation;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.net.Cidr;
import brooklyn.util.net.Protocol;
import brooklyn.util.ssh.IptablesCommands;
import brooklyn.util.ssh.IptablesCommands.Chain;
import brooklyn.util.ssh.IptablesCommands.Policy;

import com.google.common.base.Function;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.net.HostAndPort;
import com.google.inject.Module;

/**
 * A {@link Location} that wraps a Docker container.
 * <p>
 * The underlying container is presented as an {@link SshMachineLocation} obtained using the jclouds Docker driver.
 */
public class DockerContainerLocation extends SshMachineLocation implements SupportsPortForwarding, DynamicLocation<DockerContainer, DockerContainerLocation> {

    private static final Logger LOG = LoggerFactory.getLogger(DockerContainerLocation.class);

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
        Map<Integer, Integer> mapping = getPortMappingsForDocker(getOwner().getDockerHost().getJcloudsLocation());
        Integer publicPort = mapping.get(portNumber);
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
        portForwardManager.associate(dockerHost, hostPort, machine, containerPort);
    }

    private Map<Integer, Integer> getPortMappingsForDocker(JcloudsLocation docker) {
        ComputeServiceContext context = null;
        try {
            context = ContextBuilder.newBuilder("docker")
                    .endpoint(docker.getEndpoint())
                    .credentials("docker", "docker")
                    .modules(ImmutableSet.<Module>of(new SLF4JLoggingModule(), new SshjSshClientModule()))
                    .build(ComputeServiceContext.class);
            DockerApi api = context.unwrapApi(DockerApi.class);
            String containerId = getOwner().getContainerId();
            Container container = api.getRemoteApi().inspectContainer(containerId);
            Map<Integer, Integer> portMappings = Maps.newLinkedHashMap();
            Map<String, List<Map<String, String>>> ports = container.getNetworkSettings().getPorts();
            LOG.debug("Docker will forward these ports {}", ports);
            for (Map.Entry<String, List<Map<String, String>>> entrySet : ports.entrySet()) {
                String containerPort = Iterables.get(Splitter.on("/").split(entrySet.getKey()), 0);
                String hostPort = Iterables.getOnlyElement(Iterables.transform(entrySet.getValue(),
                        new Function<Map<String, String>, String>() {
                            @Override
                            public String apply(Map<String, String> hostIpAndPort) {
                                return hostIpAndPort.get("HostPort");
                            }
                        }));
                portMappings.put(Integer.parseInt(containerPort), Integer.parseInt(hostPort));
            }
            return portMappings;
        } finally {
            if (context != null) {
                context.close();
            }
        }
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
