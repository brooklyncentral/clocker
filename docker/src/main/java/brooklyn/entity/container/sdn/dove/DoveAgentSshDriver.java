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
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.basic.lifecycle.ScriptHelper;
import brooklyn.entity.container.sdn.SdnAgent;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;
import brooklyn.util.net.Urls;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.StringPredicates;
import brooklyn.util.text.Strings;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
        commands.add(BashCommands.INSTALL_CURL);
        commands.add(BashCommands.installPackage(MutableMap.builder()
                .put("yum", "iotop libvirt libvirt-python iproute")
                .put("apt", "iotop libvirt-bin libvirt-dev python-libvirt iproute2 rpm")
                .build(), null));
        commands.add(sudo("rpm --nodeps --install dove-agent.rpm"));
        commands.add("wget https://repos.fedorapeople.org/repos/openstack/openstack-icehouse/epel-6/iproute-2.6.32-130.el6ost.netns.2.x86_64.rpm");
        commands.add(sudo("rpm -Uvh iproute-2.6.32-130.el6ost.netns.2.x86_64.rpm"));

        newScript(INSTALLING)
                .body.append(commands)
                .execute();
    }

    @Override
    public void customize() {
        Networking.checkPortsValid(getPortMap());

        String netstat = getEntity().getAttribute(DoveAgent.DOCKER_HOST).execCommand("netstat -rn");
        Iterable<String> routes = Iterables.filter(Splitter.on(CharMatcher.anyOf("\r\n")).split(netstat), StringPredicates.containsLiteral("eth0"));
        String subnetAddress = getEntity().getAttribute(SoftwareProcess.SUBNET_ADDRESS);
        String address = Strings.getFirstWord(Iterables.find(routes, Predicates.and(StringPredicates.containsLiteral("255.255.255."),
                StringPredicates.containsLiteral(subnetAddress.substring(0, subnetAddress.lastIndexOf('.'))))));
        String gateway = Strings.getFirstWordAfter(Iterables.find(routes, StringPredicates.containsLiteral("10.0.0.0")), "10.0.0.0");
        LOG.debug("Found gateway {} and address {}", gateway, address);

        List<String> commands = Lists.newLinkedList();
        commands.add(sudo("brctl addbr br_mgmt_1"));
        commands.add(sudo(String.format("ifconfig br_mgmt_1 %s netmask 255.255.255.224", getEntity().getAttribute(SdnAgent.SDN_AGENT_ADDRESS).getHostAddress())));
        commands.add(sudo("ifconfig eth0 0.0.0.0"));
        commands.add(sudo(String.format("ifconfig br_mgmt_1:1 %s netmask 255.255.255.192", address)));
        commands.add(sudo("brctl addif br_mgmt_1 eth0"));
        commands.add(sudo(String.format("route add -net 10.0.0.0 netmask 255.0.0.0 gw %s", gateway)));
        commands.add(BashCommands.alternatives(sudo("service libvirtd start"), sudo("service libvirt-bin start"), "true"));
        commands.add("echo 'nameserver 8.8.8.8' | " + sudo("tee /etc/resolv.conf"));

        newScript(CUSTOMIZING)
                .body.append(commands)
                .execute();

        String doveXmlTemplate = processTemplate(entity.getConfig(DoveNetwork.CONFIGURATION_XML_TEMPLATE));
        String savedDoveXml = Urls.mergePaths(getRunDir(), "dove.xml");
        DynamicTasks.queueIfPossible(SshEffectorTasks.put(savedDoveXml).contents(doveXmlTemplate)).andWaitForSuccess();
        newScript(CUSTOMIZING)
                .body.append(sudo("mv " + savedDoveXml + " /etc/dove.xml"))
                .execute();
        LOG.debug("Copied XML configuration file to /etc directory: " + savedDoveXml);
    }

    private int createNetwork(String networkId, String tenantId) {
        Map<String, String> createNetworkData = ImmutableMap.<String, String>builder()
                .put("networkId", networkId)
                .put("networkName", networkId)
                .put("tenantId", tenantId)
                .build();
        String createNetwork = copyJsonTemplate("create_network.json", createNetworkData);
        String createNetworkOutput = callRestApi(createNetwork, "v2.0/networks");

        Map<String, String> createSubnetData = ImmutableMap.<String, String>builder()
                .put("subnetId", networkId)
                .put("subnetName", networkId)
                .put("networkId", networkId)
                .put("tenantId", tenantId)
                .build();
        String createSubnet = copyJsonTemplate("create_subnet.json", createSubnetData);
        String createSubnetOutput = callRestApi(createSubnet, "v2.0/subnets");

        int doveNetworkId = getNetworkIdForName(networkId);
        return doveNetworkId;
    }

    private String copyJsonTemplate(String fileName, Map<String, String> substitutions) {
        String contents = processTemplate("classpath://brooklyn/entity/container/sdn/dove/" + fileName, substitutions);
        String target = Urls.mergePaths(getRunDir(), fileName);
        DynamicTasks.queueIfPossible(SshEffectorTasks.put(target).contents(contents)).andWaitForSuccess();
        return target;
    }

    private String callRestApi(String jsonData, String apiPath) {
        String command = String.format("curl -i -u admin:admin -X POST -H \"Content-Type: application/json\" -d @- http://%s/%s < %s",
                getEntity().getConfig(DoveNetwork.DOVE_CONTROLLER).getHostAddress(), apiPath, jsonData);
        String output = getEntity().getAttribute(DoveAgent.DOCKER_HOST).execCommand(command);
        return output;
    }

    private int getNetworkIdForName(String networkName) {
        String command = String.format("curl -s -u admin:admin http://%s/networks", getEntity().getConfig(DoveNetwork.DOVE_CONTROLLER).getHostAddress());
        String output = getEntity().getAttribute(DoveAgent.DOCKER_HOST).execCommand(command);
        JsonParser parser = new JsonParser();
        JsonElement json = parser.parse(output);
        JsonArray networks = json.getAsJsonObject().get("networks").getAsJsonArray();
        for (JsonElement element : networks) {
            JsonObject network = element.getAsJsonObject();
            int networkId = network.get("network_id").getAsInt();
            String name = network.get("name").getAsString();
            if (name.endsWith(networkName)) {
                return networkId;
            }
        }
        throw new IllegalStateException("Cannot find network: " + networkName);
    }

    @Override
    public void launch() {
        newScript(MutableMap.of(USE_PID_FILE, false), LAUNCHING)
                .body.append(sudo("start doved"))
                .execute();

        int networkId = createNetwork(getEntity().getApplicationId(), "clocker");
        getEntity().setAttribute(DoveNetwork.DOVE_BRIDGE_ID, networkId);

        Map<String, String> createBridgeData = ImmutableMap.<String, String>builder()
                .put("networkId", Integer.toString(networkId))
                .put("agentAddress", getEntity().getAttribute(SdnAgent.SDN_AGENT_ADDRESS).getHostAddress())
                .build();
        String createBridge = copyJsonTemplate("create_bridge.json", createBridgeData);
        String createBridgeOutput = callRestApi(createBridge, "api/dove/vrmgr/vnids/vm_mgr");
        Preconditions.checkState(Strings.containsLiteral(createBridgeOutput, "successfully"), "Failed to export network %s", networkId);

        String bridge = getEntity().getAttribute(DoveAgent.DOCKER_HOST).execCommand(sudo("brctl show"));
        String doveBridge = Strings.getFirstWordAfter(bridge, "dovebr_");
        if (Integer.valueOf(doveBridge) != networkId) {
            throw new IllegalStateException("Incorrect network ID found: " + doveBridge);
        }
        LOG.debug("Added bridge: " + doveBridge);

        LOG.info("SDN agent restarting Docker service");
        String restart = getEntity().getAttribute(DoveAgent.DOCKER_HOST).execCommand(sudo("service docker restart"));
        Iterable<String> successes = Iterables.filter(Splitter.on(CharMatcher.anyOf("\r\n")).split(restart), StringPredicates.containsLiteral("OK"));
        if (Iterables.size(successes) != 2) {
            throw new IllegalStateException("Failed to restart Docker Engine service: " + restart);
        }
    }

    @Override
    public boolean isRunning() {
        ScriptHelper helper = newScript(MutableMap.of(USE_PID_FILE, false), CHECK_RUNNING)
                .body.append(sudo("status doved"))
                .noExtraOutput()
                .gatherOutput();
        helper.execute();
        return helper.getResultStdout().contains("running");
    }

    @Override
    public void stop() {
        newScript(MutableMap.of(USE_PID_FILE, false), STOPPING)
                .body.append(sudo("stop doved"))
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
