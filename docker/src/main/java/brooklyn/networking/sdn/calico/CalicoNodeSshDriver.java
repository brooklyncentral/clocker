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
package brooklyn.networking.sdn.calico;

import static org.apache.brooklyn.util.ssh.BashCommands.sudo;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.net.HostAndPort;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.entity.group.AbstractGroup;
import org.apache.brooklyn.entity.software.base.AbstractSoftwareProcessSshDriver;
import org.apache.brooklyn.entity.software.base.lifecycle.ScriptHelper;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.net.Cidr;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.ssh.BashCommands;
import org.apache.brooklyn.util.text.Strings;

import brooklyn.entity.container.docker.DockerContainer;
import brooklyn.entity.nosql.etcd.EtcdNode;
import brooklyn.networking.sdn.SdnAgent;

public class CalicoNodeSshDriver extends AbstractSoftwareProcessSshDriver implements CalicoNodeDriver {

    private static final Logger LOG = LoggerFactory.getLogger(CalicoNode.class);

    public CalicoNodeSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public void postLaunch() {
    }

    public String getCalicoCommand() {
        return Os.mergePathsUnix(getInstallDir(), "calicoctl");
    }

    @Override
    public void preInstall() {
        resolver = Entities.newDownloader(this);
    }

    @Override
    public void install() {
        List<String> commands = Lists.newLinkedList();
        commands.addAll(BashCommands.commandsToDownloadUrlsAs(resolver.getTargets(), getCalicoCommand()));
        commands.add("chmod 755 " + getCalicoCommand());
        commands.add(BashCommands.installPackage("ipset"));
        commands.add(sudo("modprobe ip6_tables"));
        commands.add(sudo("modprobe xt_set"));

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
        InetAddress address = getEntity().sensors().get(SdnAgent.SDN_AGENT_ADDRESS);
        Boolean firstMember = getEntity().sensors().get(AbstractGroup.FIRST_MEMBER);
        LOG.info("Launching {} calico service at {}", Boolean.TRUE.equals(firstMember) ? "first" : "next", address.getHostAddress());

        newScript(MutableMap.of(USE_PID_FILE, false), LAUNCHING)
                .updateTaskAndFailOnNonZeroResultCode()
                .body.append(sudo(String.format("%s node --ip=%s", getCalicoCommand(), address.getHostAddress())))
                .execute();
    }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of(USE_PID_FILE, false), CHECK_RUNNING)
                .body.append(sudo(String.format("%s status", getCalicoCommand())))
                .execute() == 0;
    }

    @Override
    public void stop() {
        newScript(MutableMap.of(USE_PID_FILE, false), STOPPING)
                .body.append(sudo(getCalicoCommand() + " node stop --force"))
                .execute();
    }

    @Override
    public void createSubnet(String virtualNetworkId, String subnetId, Cidr subnetCidr) {
        boolean ipip = entity.config().get(CalicoNetwork.USE_IPIP);
        boolean nat = entity.config().get(CalicoNetwork.USE_NAT);
        newScript("createSubnet")
                .body.append(
                        sudo(String.format("%s pool add %s %s %s", getCalicoCommand(), subnetCidr, ipip ? "--ipip" : "", nat ? "--nat-outgoing" : "")))
                .execute();
    }

    /** For Calico we use profiles to group containers in networks and add the required IP address to the eth1 calico interface. */
    @Override
    public InetAddress attachNetwork(String containerId, String subnetId) {
        InetAddress address = getEntity().sensors().get(SdnAgent.SDN_PROVIDER).getNextContainerAddress(subnetId);

        // Run some commands to get information about the container network namespace
        String ipAddrOutput = getEntity().sensors().get(SdnAgent.DOCKER_HOST).execCommand(sudo("ip addr show dev docker0 scope global label docker0"));
        String dockerIp = Strings.getFirstWordAfter(ipAddrOutput.replace('/', ' '), "inet");
        String dockerPid = Strings.trimEnd(getEntity().sensors().get(SdnAgent.DOCKER_HOST).runDockerCommand("inspect -f '{{.State.Pid}}' " + containerId));
        Cidr subnetCidr = getEntity().sensors().get(SdnAgent.SDN_PROVIDER).getSubnetCidr(subnetId);
        InetAddress agentAddress = getEntity().sensors().get(SdnAgent.SDN_AGENT_ADDRESS);

        // Determine whether we are attatching the container to the initial application network
        boolean initial = false;
        for (Entity container : getEntity().sensors().get(SdnAgent.DOCKER_HOST).getDockerContainerList()) {
            if (containerId.equals(container.sensors().get(DockerContainer.CONTAINER_ID))) {
                Entity running = container.sensors().get(DockerContainer.ENTITY);
                String applicationId = running.getApplicationId();
                if (subnetId.equals(applicationId)) {
                    initial = true;
                }
                break;
            }
        }

        // Add the container if we have not yet done so
        if (initial) {
            newScript("addContainer")
                    .body.append(sudo(String.format("%s container add %s %s", getCalicoCommand(), containerId, address.getHostAddress())))
                    .execute();

            // Return its endpoint ID
            ScriptHelper getEndpointId = newScript("getEndpointId")
                    .body.append(sudo(String.format("%s container %s endpoint-id show", getCalicoCommand(), containerId)))
                    .noExtraOutput()
                    .gatherOutput();
            getEndpointId.execute();
            String endpointId = Strings.getFirstWord(getEndpointId.getResultStdout());

            // Add to the application profile
            newScript("addCalico")
                    .body.append( // Idempotent
                            sudo(String.format("%s profile add %s", getCalicoCommand(), subnetId)),
                            sudo(String.format("%s endpoint %s profile append %s", getCalicoCommand(), endpointId, subnetId)))
                    .execute();
        }

        // Set up the network
        List<String> commands = MutableList.of();
        commands.add(sudo("mkdir -p /var/run/netns"));
        commands.add(BashCommands.ok(sudo(String.format("ln -s /proc/%s/ns/net /var/run/netns/%s", dockerPid, dockerPid))));
        if (initial) {
            commands.add(sudo(String.format("ip netns exec %s ip route del default", dockerPid)));
            commands.add(sudo(String.format("ip netns exec %s ip route add default via %s", dockerPid, dockerIp)));
            commands.add(sudo(String.format("ip netns exec %s ip route add %s via %s", dockerPid, subnetCidr.toString(), agentAddress.getHostAddress())));
        } else {
            commands.add(sudo(String.format("ip netns exec %s ip addr add %s/%d dev eth1", dockerPid, address.getHostAddress(), subnetCidr.getLength())));
        }
        newScript("setupNetwork")
                .body.append(commands)
                .execute();

        return address;
    }

    @Override
    public Map<String, String> getShellEnvironment() {
        Entity etcdNode = getEntity().config().get(CalicoNode.ETCD_NODE);
        HostAndPort etcdAuthority = HostAndPort.fromParts(etcdNode.sensors().get(Attributes.SUBNET_ADDRESS), etcdNode.sensors().get(EtcdNode.ETCD_CLIENT_PORT));
        Map<String, String> environment = MutableMap.copyOf(super.getShellEnvironment());
        environment.put("ETCD_AUTHORITY", etcdAuthority.toString());
        return environment;
    }

}
