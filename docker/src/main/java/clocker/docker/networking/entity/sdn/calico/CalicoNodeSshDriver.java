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
package clocker.docker.networking.entity.sdn.calico;

import static org.apache.brooklyn.util.ssh.BashCommands.sudo;

import io.brooklyn.entity.nosql.etcd.EtcdNode;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clocker.docker.networking.entity.sdn.DockerNetworkAgentSshDriver;
import clocker.docker.networking.entity.sdn.SdnAgent;

import com.google.common.collect.Lists;
import com.google.common.net.HostAndPort;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.entity.group.AbstractGroup;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.net.Cidr;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.ssh.BashCommands;

public class CalicoNodeSshDriver extends DockerNetworkAgentSshDriver implements CalicoNodeDriver {

    private static final Logger LOG = LoggerFactory.getLogger(CalicoNode.class);

    public CalicoNodeSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public String getDockerNetworkDriver() {
        return "calico";
    }

    @Override
    public void postLaunch() {
    }

    public String getCalicoCommand() {
        return Os.mergePathsUnix(getInstallDir(), "calicoctl");
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
                .body.append(sudo(String.format("%s node --libnetwork --ip=%s", getCalicoCommand(), address.getHostAddress())))
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
    public List<String> getDockerNetworkCreateOpts() {
        boolean ipip = entity.config().get(CalicoNetwork.USE_IPIP);
        boolean nat = entity.config().get(CalicoNetwork.USE_NAT);

        List<String> opts = MutableList.copyOf(super.getDockerNetworkCreateOpts());
        if (ipip) opts.add("ipip=true");
        if (nat) opts.add("nat-outgoing=true");
        return opts;
    }

    @Override
    public void createSubnet(String subnetId, Cidr subnetCidr) {
        boolean ipip = entity.config().get(CalicoNetwork.USE_IPIP);
        boolean nat = entity.config().get(CalicoNetwork.USE_NAT);
        newScript("createSubnet")
                .body.append(
                        sudo(String.format("%s pool add %s %s %s", getCalicoCommand(), subnetCidr, ipip ? "--ipip" : "", nat ? "--nat-outgoing" : "")))
                .execute();
        super.createSubnet(subnetId, subnetCidr);
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
