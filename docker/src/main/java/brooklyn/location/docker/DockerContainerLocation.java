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

import brooklyn.entity.Entity;
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

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.ImmutableList;

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

    @SetFromFlag("entity")
    private Entity entity;

    @Override
    public void init() {
        super.init();
        setEntity(entity);
    }

    public void setEntity(Entity entity) {
        dockerContainer.setRunningEntity(entity);
    }

    public Entity getEntity() {
        return dockerContainer.getRunningEntity();
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
        if (getOwner().getDockerHost().getInfrastructure().getConfig(DockerInfrastructure.OPEN_IPTABLES)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Using iptables to add access for TCP/{} to {}", port, machine);
            }
            List<String> commands = ImmutableList.of(
                    IptablesCommands.insertIptablesRule(Chain.INPUT, Protocol.TCP, port, Policy.ACCEPT),
                    IptablesCommands.saveIptablesRules(),
                    IptablesCommands.listIptablesRule());
            int result = machine.execCommands(String.format("Open iptables TCP/%d", port), commands);
            if (result != 0) {
                String msg = String.format("Error running iptables update for TCP/{} on {}", port, machine);
                LOG.error(msg);
                throw new RuntimeException(msg);
            }
        }
    }

    @Override
    public boolean obtainSpecificPort(int portNumber) {
        boolean result = machine.obtainSpecificPort(portNumber);
        if (result) {
            addIptablesRule(portNumber);
        }
        return result;
    }

    @Override
    public int obtainPort(PortRange range) {
        int portNumber = machine.obtainPort(range);
        addIptablesRule(portNumber);
        return portNumber;
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
