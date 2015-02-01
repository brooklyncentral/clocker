/*
 * Copyright 2014 by Cloudsoft Corporation Limited
 */
package brooklyn.entity.container.sdn.weave;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractGroup;
import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Cidr;
import brooklyn.util.net.Networking;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.task.Tasks;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

public class WeaveContainerSshDriver extends AbstractSoftwareProcessSshDriver implements WeaveContainerDriver {

    private static final Logger LOG = LoggerFactory.getLogger(WeaveContainer.class);

    public WeaveContainerSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public void postLaunch() {
    }

    @Override
    public Set<Integer> getPortsUsed() {
        return ImmutableSet.<Integer>builder()
                .addAll(super.getPortsUsed())
                .addAll(getPortMap().values())
                .build();
    }

    protected Map<String, Integer> getPortMap() {
        return MutableMap.of("weave", getEntity().getConfig(WeaveContainer.WEAVE_PORT));
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
        commands.add(BashCommands.installPackage("ethtool conntrack"));
        commands.addAll(BashCommands.commandsToDownloadUrlsAs(resolver.getTargets(), getWeaveCommand()));
        commands.add("chmod 755 " + getWeaveCommand());

        newScript(INSTALLING)
                .body.append(commands)
                .execute();
    }

    @Override
    public void customize() {
        Networking.checkPortsValid(getPortMap());

        newScript(CUSTOMIZING).execute();
    }

    @Override
    public void launch() {
        InetAddress address = getEntity().getAttribute(WeaveContainer.SDN_AGENT_ADDRESS);
        Boolean firstMember = getEntity().getAttribute(AbstractGroup.FIRST_MEMBER);
        Entity first = getEntity().getAttribute(AbstractGroup.FIRST);
        LOG.info("Launching {} Weave service at {}", Boolean.TRUE.equals(firstMember) ? "first" : "next", address.getHostAddress());

        newScript(MutableMap.of(USE_PID_FILE, false), LAUNCHING)
                .updateTaskAndFailOnNonZeroResultCode()
                .body.append(BashCommands.sudo(String.format("%s launch %s", getWeaveCommand(),
                        Boolean.TRUE.equals(firstMember) ? "" : first.getAttribute(Attributes.SUBNET_ADDRESS))))
                .execute();
    }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of(USE_PID_FILE, false), CHECK_RUNNING)
                .body.append(BashCommands.sudo(getWeaveCommand() + " status"))
                .execute() == 0;
    }

    @Override
    public void stop() {
        newScript(MutableMap.of(USE_PID_FILE, false), STOPPING)
                .body.append(BashCommands.sudo(getWeaveCommand() + " stop"))
                .execute();
    }

    @Override
    public void attachNetwork(String containerId, InetAddress address) {
        Tasks.setBlockingDetails("Attach Weave to " + containerId);
        try {
            Cidr cidr = getEntity().getConfig(WeaveNetwork.CIDR);
            ((WeaveContainer) getEntity()).getDockerHost().execCommand(BashCommands.sudo(String.format("%s attach %s/%d %s",
                    getWeaveCommand(), address.getHostAddress(), cidr.getLength(), containerId)));
        } finally {
            Tasks.resetBlockingDetails();
        }
    }

}
