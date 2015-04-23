/*
 * Copyright 2014-2015 by Cloudsoft Corporation Limited
 */
package brooklyn.networking.sdn.calico;

import static brooklyn.util.ssh.BashCommands.sudo;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractGroup;
import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.container.docker.DockerContainer;
import brooklyn.entity.nosql.etcd.EtcdNode;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.networking.sdn.SdnAgent;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Cidr;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.text.Strings;

import com.google.common.collect.Lists;
import com.google.common.net.HostAndPort;

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
        InetAddress address = getEntity().getAttribute(SdnAgent.SDN_AGENT_ADDRESS);
        Boolean firstMember = getEntity().getAttribute(AbstractGroup.FIRST_MEMBER);
        LOG.info("Launching {} calico service at {}", Boolean.TRUE.equals(firstMember) ? "first" : "next", address);

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
        newScript("createSubnet")
                .body.append(
                        sudo(String.format("%s ipv4 pool add %s", getCalicoCommand(), subnetCidr)))
                .execute();
    }

    /** For Calico we use profiles to group containers in networks and add the required IP address to the eth1 calico interface. */
    @Override
    public InetAddress attachNetwork(String containerId, String subnetId) {
        InetAddress address = getEntity().getAttribute(SdnAgent.SDN_PROVIDER).getNextContainerAddress(subnetId);

        // Run some commands to get information about the container network namespace
        String ipAddrOutput = getEntity().getAttribute(SdnAgent.DOCKER_HOST).execCommand(sudo("ip addr show dev docker0 scope global label docker0"));
        String dockerIp = Strings.getFirstWordAfter(ipAddrOutput.replace('/', ' '), "inet");
        String dockerPid = Strings.trimEnd(getEntity().getAttribute(SdnAgent.DOCKER_HOST).runDockerCommand("inspect -f '{{.State.Pid}}' " + containerId));
        Cidr subnetCidr = getEntity().getAttribute(SdnAgent.SDN_PROVIDER).getSubnetCidr(subnetId);
        InetAddress agentAddress = getEntity().getAttribute(SdnAgent.SDN_AGENT_ADDRESS);

        // Determine whether we are attatching the container to the initial application network
        boolean initial = false;
        for (Entity container : getEntity().getAttribute(SdnAgent.DOCKER_HOST).getDockerContainerList()) {
            if (containerId.equals(container.getAttribute(DockerContainer.CONTAINER_ID))) {
                Entity running = container.getAttribute(DockerContainer.ENTITY);
                String applicationId = running.getApplicationId();
                if (subnetId.equals(applicationId)) {
                    initial = true;
                }
                break;
            }
        }

        // Add the container to the application profile and set up the network
        List<String> commands = MutableList.of();
        if (initial) {
            commands.add(sudo(String.format("%s container add %s %s", getCalicoCommand(), containerId, address.getHostAddress())));
            commands.add(sudo(String.format("%s profile add %s", getCalicoCommand(), subnetId))); // Idempotent
            commands.add(sudo(String.format("%s profile %s member add %s", getCalicoCommand(), subnetId, containerId)));
            commands.add(sudo(String.format("ip netns exec %s ip route del default", dockerPid)));
            commands.add(sudo(String.format("ip netns exec %s ip route add default via %s", dockerPid, dockerIp)));
            commands.add(sudo(String.format("ip netns exec %s ip route add %s via %s", dockerPid, subnetCidr.toString(), agentAddress.getHostAddress())));
        } else {
            commands.add(sudo(String.format("ip netns exec %s ip addr add %s/%d dev eth1", dockerPid, address.getHostAddress(), subnetCidr.getLength())));
        }

        newScript("attachNetwork")
                .body.append(commands)
                .execute();

        return address;
    }

    @Override
    public Map<String, String> getShellEnvironment() {
        Entity etcdNode = getEntity().config().get(CalicoNode.ETCD_NODE);
        HostAndPort etcdAuthority = HostAndPort.fromParts(etcdNode.getAttribute(Attributes.ADDRESS), etcdNode.getAttribute(EtcdNode.ETCD_CLIENT_PORT));
        Map<String, String> environment = MutableMap.copyOf(super.getShellEnvironment());
        environment.put("ETCD_AUTHORITY", etcdAuthority.toString());
        return environment;
    }

}
