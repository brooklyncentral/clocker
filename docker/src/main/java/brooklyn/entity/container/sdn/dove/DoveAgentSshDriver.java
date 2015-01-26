/*
 * Copyright 2014 by Cloudsoft Corporation Limited
 */
package brooklyn.entity.container.sdn.dove;

import static brooklyn.util.ssh.BashCommands.sudo;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.container.sdn.SdnAgent;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;
import brooklyn.util.net.Urls;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Strings;
import brooklyn.util.text.StringPredicates;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class DoveAgentSshDriver extends AbstractSoftwareProcessSshDriver implements DoveAgentDriver {

    private static final Logger LOG = LoggerFactory.getLogger(DoveAgent.class);

    public DoveAgentSshDriver(EntityLocal entity, SshMachineLocation machine) {
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
        return MutableMap.<String, Integer>of();
    }

    @Override
    public void preInstall() {
        resolver = Entities.newDownloader(this);
    }

    @Override
    public void install() {
        List<String> commands = Lists.newLinkedList();
        commands.addAll(BashCommands.commandsToDownloadUrlsAs(resolver.getTargets(), "dove-agent.rpm"));
        commands.add(BashCommands.installPackage("iotop libvirt libvirt-python iproute"));
        commands.add(BashCommands.sudo("rpm --nodeps --install dove-agent.rpm"));

        newScript(INSTALLING)
                .body.append(commands)
                .execute();
    }

    @Override
    public void customize() {
        Networking.checkPortsValid(getPortMap());

        String routes = getEntity().getAttribute(DoveAgent.DOCKER_HOST).execCommand("netstat -r");
        LOG.debug("Current routes: " + routes);
        String address = Strings.getFirstWord(Iterables.find(Splitter.on("\n").split(routes), StringPredicates.containsLiteral("255.255.255.192")));
        String gateway = Strings.getFirstWordAfter("10.0.0.0", Iterables.find(Splitter.on("\n").split(routes), StringPredicates.containsLiteral("10.0.0.0")));
        LOG.debug("Found gateway {} and address {}", gateway, address);

        List<String> commands = Lists.newLinkedList();
        commands.add(sudo("brctl addbr br_mgmt_1"));
        commands.add(sudo(String.format("ifconfig br_mgmt_1 %s netmask 255.255.255.224", getEntity().getAttribute(SdnAgent.SDN_AGENT_ADDRESS).getHostAddress())));
        commands.add(sudo("ifconfig eth0 0.0.0.0"));
        commands.add(sudo(String.format("ifconfig br_mgmt_1:1 %s netmask 255.255.255.192", address)));
        commands.add(sudo("brctl addif br_mgmt_1 eth0"));
        commands.add(sudo(String.format("route add -net 10.0.0.0 netmask 255.0.0.0 gw %s", gateway)));
        commands.add("/etc/init.d/libvirtd start");

        newScript(CUSTOMIZING)
                .body.append(commands)
                .execute();

        String bridge = getEntity().getAttribute(DoveAgent.DOCKER_HOST).execCommand(BashCommands.sudo("brctl show"));
        String bridgeId = Strings.getFirstWordAfter("dovebr_", bridge);
        String addBridgeCommand = String.format("echo \"{ \"vnid_list\": \"8064181\", \"ip_addr\": \"%s\" }\" | curl -i -u admin:admin -X POST -H \"Content-Type: application/json\" -d @- http://%s/api/dove/vrmgr/vnids/vm_mgr",
                bridgeId, getEntity().getAttribute(SdnAgent.SDN_AGENT_ADDRESS).getHostAddress());
        LOG.debug("Add bridge command: " + addBridgeCommand);

        String addBridgeOutput = getEntity().getAttribute(DoveAgent.DOCKER_HOST).execCommand(addBridgeCommand);
        LOG.debug("Added bridge: " + addBridgeOutput);

        String doveXmlTemplate = processTemplate(entity.getConfig(DoveNetwork.CONFIGURATION_XML_TEMPLATE));
        String savedDoveXml = Urls.mergePaths(getRunDir(), "dove.xml");
        DynamicTasks.queueIfPossible(SshEffectorTasks.put(savedDoveXml).contents(doveXmlTemplate)).andWaitForSuccess();
        newScript(CUSTOMIZING)
                .body.append(sudo("mv " + savedDoveXml + " /etc/dove.xml"))
                .execute();
        LOG.debug("Copied XML configuration file to /etc directory: " + savedDoveXml);
    }

    @Override
    public void launch() {
        newScript(MutableMap.of(USE_PID_FILE, false), LAUNCHING)
                .body.append(BashCommands.sudo("start doved"))
                .execute();
    }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of(USE_PID_FILE, false), CHECK_RUNNING)
                .body.append(BashCommands.sudo("status doved | grep PID"))
                .execute() == 0;
    }

    @Override
    public void stop() {
        newScript(MutableMap.of(USE_PID_FILE, false), STOPPING)
                .body.append(BashCommands.sudo("stop doved"))
                .execute();
    }

    @Override
    public InetAddress attachNetwork(String containerId) {
        Tasks.setBlockingDetails("Attach to " + containerId);
        try {
            /*
             * ./setup_network_v2.sh containerid network_1 port_null_12345678 fa:16:50:00:01:e1 50.0.0.2/24 50.0.0.1 8064181
             */
            InetAddress address = getEntity().getConfig(DoveAgent.SDN_PROVIDER).get();
            return address;
        } finally {
            Tasks.resetBlockingDetails();
        }
    }

}
