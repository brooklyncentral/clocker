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
package clocker.mesos.location.framework.marathon;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clocker.docker.entity.container.DockerContainer;
import clocker.mesos.entity.task.marathon.MarathonTask;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.base.Optional;
import com.google.common.net.HostAndPort;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationDefinition;
import org.apache.brooklyn.api.location.PortRange;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.location.HasSubnetHostname;
import org.apache.brooklyn.core.location.access.PortForwardManager;
import org.apache.brooklyn.core.location.dynamic.DynamicLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.net.Cidr;
import org.apache.brooklyn.util.net.Protocol;

import brooklyn.networking.common.subnet.PortForwarder;

/**
 * A {@link Location} that wraps a Marathon task; i.e. a Docker container.
 */
public class MarathonTaskLocation extends SshMachineLocation implements HasSubnetHostname, DynamicLocation<MarathonTask, MarathonTaskLocation> { // SupportsPortForwarding

    /** serialVersionUID */
    private static final long serialVersionUID = 610389734596906782L;

    private static final Logger LOG = LoggerFactory.getLogger(MarathonTaskLocation.class);

    @SetFromFlag("entity")
    private Entity entity;

    @SetFromFlag("owner")
    private MarathonTask marathonTask;

    @Override
    public void init() {
        super.init();
    }

    @Override
    public LocationDefinition register() {
        throw new UnsupportedOperationException("Marathon task location type definition cannot be persisted");
    }

    @Override
    public void deregister() {
        // no-op
    }

    @Override
    public MarathonTask getOwner() {
        return marathonTask;
    }

    public Entity getEntity() {
        return entity;
    }

    public int getMappedPort(int portNumber) {
        Map<Integer, String> mapping = config().get(SshMachineLocation.TCP_PORT_MAPPINGS);
        for (Map.Entry<Integer, String> entry : mapping.entrySet()) {
            int containerPort = HostAndPort.fromString(entry.getValue()).getPort();
            if (containerPort == portNumber) {
                return entry.getKey();
            }
        }
        return -1;
    }

    @Override
    public boolean obtainSpecificPort(int portNumber) {
        int targetPort = getMappedPort(portNumber);
        mapPort(targetPort, portNumber);
        return true;
    }

    @Override
    public int obtainPort(PortRange range) {
        int portNumber = range.iterator().next();
        int targetPort = getMappedPort(portNumber);
        mapPort(targetPort, portNumber);
        return portNumber;
    }

    private void mapPort(int hostPort, int containerPort) {
        String dockerHost = getAddress().getHostAddress();
        PortForwardManager portForwardManager = getOwner().getMesosCluster().getMesosSlave(dockerHost).getSubnetTier().getPortForwardManager();
        portForwardManager.associate(dockerHost, HostAndPort.fromParts(dockerHost, hostPort), this, containerPort);
        PortForwarder portForwarder = getOwner().getMesosCluster().getMesosSlave(dockerHost).getSubnetTier().getPortForwarder();
        portForwarder.openPortForwarding(this, containerPort, Optional.of(hostPort), Protocol.TCP, Cidr.UNIVERSAL);
        portForwarder.openFirewallPort(entity, hostPort, Protocol.TCP, Cidr.UNIVERSAL);
    }

    @Override
    public int execScript(Map<String,?> props, String summaryForLogging, List<String> commands, Map<String,?> env) {
        if (getOwner().config().get(DockerContainer.DOCKER_USE_SSH)) {
            return super.execScript(props, summaryForLogging, commands, env);
        } else {
            return 0;
        }
    }

    @Override
    public int execCommands(Map<String,?> props, String summaryForLogging, List<String> commands, Map<String,?> env) {
        if (getOwner().config().get(DockerContainer.DOCKER_USE_SSH)) {
            return super.execCommands(props, summaryForLogging, commands, env);
        } else {
            return 0;
        }
    }

    @Override
    public void releasePort(int portNumber) { }

    @Override
    public InetAddress getAddress() {
        String address = getOwner().sensors().get(Attributes.ADDRESS);
        try {
            return InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public String getSubnetHostname() {
        return Optional.fromNullable(getOwner().sensors().get(Attributes.SUBNET_HOSTNAME)).or(getOwner().sensors().get(Attributes.HOSTNAME));
    }

    @Override
    public String getSubnetIp() {
        return Optional.fromNullable(getOwner().sensors().get(Attributes.SUBNET_ADDRESS)).or(getOwner().sensors().get(Attributes.ADDRESS));
    }


    @Override
    public void close() throws IOException {
        LOG.debug("Close called on Marathon task: {}", this);
        try {
            if (marathonTask.sensors().get(MarathonTask.SERVICE_UP)) {
                LOG.info("Stopping Marathon task entity for {}: {}", this, marathonTask);
                marathonTask.stop();
            }
            LOG.info("Marathon task closed: {}", this);
        } catch (Exception e) {
            LOG.warn("Error closing Marathon task {}: {}", this, e.getMessage());
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public ToStringHelper string() {
        return super.string()
                .add("entity", entity)
                .add("owner", marathonTask);
    }
}
