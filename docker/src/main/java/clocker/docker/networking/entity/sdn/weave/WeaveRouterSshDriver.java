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
package clocker.docker.networking.entity.sdn.weave;

import static org.apache.brooklyn.util.ssh.BashCommands.chain;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clocker.docker.entity.DockerInfrastructure;
import clocker.docker.networking.entity.sdn.DockerNetworkAgentSshDriver;
import clocker.docker.networking.entity.sdn.SdnAgent;
import clocker.docker.networking.entity.sdn.SdnProvider;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.entity.group.AbstractGroup;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.ssh.BashCommands;

public class WeaveRouterSshDriver extends DockerNetworkAgentSshDriver implements WeaveRouterDriver {

    private static final Logger LOG = LoggerFactory.getLogger(WeaveRouter.class);

    public WeaveRouterSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public String getDockerNetworkDriver() {
        return "weave";
    }

    public String getWeaveCommand() {
        return Os.mergePathsUnix(getInstallDir(), "weave");
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
        InetAddress address = getEntity().sensors().get(WeaveRouter.SDN_AGENT_ADDRESS);
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
                                        entity.config().get(WeaveRouter.WEAVE_PROXY_PORT),
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
    public Map<String, String> getShellEnvironment() {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.<String, String> builder()
                .putAll(super.getShellEnvironment())
                .put("CHECKPOINT_DISABLE", "1")
                .put("WEAVE_VERSION", entity.config().get(WeaveRouter.SUGGESTED_VERSION))
                .put("VERSION", entity.config().get(WeaveRouter.SUGGESTED_VERSION));
        return builder.build();
    }

}
