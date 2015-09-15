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
package brooklyn.networking.sdn.weave;

import static org.apache.brooklyn.util.ssh.BashCommands.chain;

import java.net.InetAddress;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.entity.group.AbstractGroup;
import org.apache.brooklyn.entity.software.base.AbstractSoftwareProcessSshDriver;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.net.Cidr;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.ssh.BashCommands;

import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.networking.sdn.SdnAgent;
import brooklyn.networking.sdn.SdnProvider;

public class WeaveContainerSshDriver extends AbstractSoftwareProcessSshDriver implements WeaveContainerDriver {

    private static final Logger LOG = LoggerFactory.getLogger(WeaveContainer.class);

    public WeaveContainerSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    public String getWeaveCommand() {
        return Os.mergePathsUnix(getInstallDir(), "weave");
    }

    @Override
    public void preInstall() {
        resolver = Entities.newDownloader(this);
    }

    @Override
    public void install() {
        List<String> commands = Lists.newLinkedList();
        commands.addAll(BashCommands.commandsToDownloadUrlsAs(resolver.getTargets(), getWeaveCommand()));
        commands.add("chmod 755 " + getWeaveCommand());

        newScript(INSTALLING)
                .body.append(commands)
                .execute();
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING).execute();
    }

    @Override
    public void launch() {
        InetAddress address = getEntity().sensors().get(WeaveContainer.SDN_AGENT_ADDRESS);
        Boolean firstMember = getEntity().sensors().get(AbstractGroup.FIRST_MEMBER);
        Entity first = getEntity().sensors().get(AbstractGroup.FIRST);
        LOG.info("Launching {} Weave service at {}", Boolean.TRUE.equals(firstMember) ? "first" : "next", address.getHostAddress());

        if (entity.config().get(DockerInfrastructure.DOCKER_GENERATE_TLS_CERTIFICATES)) {
            newScript(ImmutableMap.of(NON_STANDARD_LAYOUT, "true"), CUSTOMIZING)
                    .body.append(
                            String.format("cp ca-cert.pem %s/ca.pem", getRunDir()),
                            String.format("cp server-cert.pem %s/cert.pem", getRunDir()),
                            String.format("cp server-key.pem %s/key.pem", getRunDir()))
                    .failOnNonZeroResultCode()
                    .execute();
        }

        newScript(MutableMap.of(USE_PID_FILE, false), LAUNCHING)
                .body.append(
                        chain(
                                BashCommands.sudo(String.format("%s launch-router --ipalloc-range %s %s",
                                        getWeaveCommand(),
                                        entity.config().get(SdnProvider.CONTAINER_NETWORK_CIDR),
                                        Boolean.TRUE.equals(firstMember) ? "" : first.sensors().get(Attributes.SUBNET_ADDRESS))),
                                BashCommands.sudo(String.format("%s launch-proxy -H tcp://[::]:%d --tlsverify --tls --tlscert=%s/cert.pem --tlskey=%<s/key.pem --tlscacert=%<s/ca.pem",
                                        getWeaveCommand(),
                                        entity.config().get(WeaveContainer.WEAVE_PROXY_PORT),
                                        getRunDir()))))
                .failOnNonZeroResultCode()
                .uniqueSshConnection()
                .execute();
    }

    @Override
    public boolean isRunning() {
        // Spawns a container for duration of command, so take the host lock
        getEntity().sensors().get(SdnAgent.DOCKER_HOST).getDynamicLocation().getLock().lock();
        try {
            return newScript(MutableMap.of(USE_PID_FILE, false), CHECK_RUNNING)
                    .body.append(BashCommands.sudo(getWeaveCommand() + " status"))
                    .execute() == 0;
        } finally {
            getEntity().sensors().get(SdnAgent.DOCKER_HOST).getDynamicLocation().getLock().unlock();
        }
    }

    @Override
    public void stop() {
        newScript(MutableMap.of(USE_PID_FILE, false), STOPPING)
                .body.append(BashCommands.sudo(getWeaveCommand() + " stop"))
                .execute();
    }

    @Override
    public void createSubnet(String svirtualNetworkId, String subnetId, Cidr subnetCidr) {
        LOG.debug("Nothing to do for Weave subnet creation");
    }

    @Override
    public InetAddress attachNetwork(String containerId, String subnetId) {
        Tasks.setBlockingDetails(String.format("Attach %s to %s", containerId, subnetId));
        try {
            Cidr cidr = getEntity().sensors().get(SdnAgent.SDN_PROVIDER).getSubnetCidr(subnetId);
            InetAddress address = getEntity().sensors().get(SdnAgent.SDN_PROVIDER).getNextContainerAddress(subnetId);
            ((WeaveContainer) getEntity()).getDockerHost().execCommand(BashCommands.sudo(String.format("%s attach %s/%d %s",
                    getWeaveCommand(), address.getHostAddress(), cidr.getLength(), containerId)));
            return address;
        } finally {
            Tasks.resetBlockingDetails();
        }
    }

}
