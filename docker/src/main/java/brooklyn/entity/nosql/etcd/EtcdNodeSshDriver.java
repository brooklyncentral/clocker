/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.entity.nosql.etcd;

import static java.lang.String.format;

import java.util.List;
import java.util.Map;
import java.util.Set;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.lifecycle.ScriptHelper;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.text.Strings;

import com.google.common.base.CharMatcher;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class EtcdNodeSshDriver extends AbstractSoftwareProcessSshDriver implements EtcdNodeDriver {

    public EtcdNodeSshDriver(final EtcdNodeImpl entity, final SshMachineLocation machine) {
        super(entity, machine);

        entity.setAttribute(Attributes.LOG_FILE_LOCATION, getLogFileLocation());
    }

    @Override
    public EtcdNodeImpl getEntity() {
        return EtcdNodeImpl.class.cast(super.getEntity());
    }

    @Override
    public Set<Integer> getPortsUsed() {
        return ImmutableSet.<Integer>builder()
                .addAll(super.getPortsUsed())
                .addAll(getPortMap().values())
                .build();
    }

    protected Map<String, Integer> getPortMap() {
        return MutableMap.of("clientPort", getEntity().getAttribute(EtcdNode.ETCD_CLIENT_PORT), "peerPort", getEntity().getAttribute(EtcdNode.ETCD_PEER_PORT));
    }

    @Override
    public void preInstall() {
        resolver = Entities.newDownloader(this);
        setExpandedInstallDir(Os.mergePaths(getInstallDir(), resolver.getUnpackedDirectoryName(format("etcd-v%s-linux-amd64", getVersion()))));
    }

    @Override
    public void install() {
        OsDetails osDetails = getMachine().getMachineDetails().getOsDetails();
        if (!osDetails.isLinux()) {
            throw new IllegalStateException("Machine was not detected as linux: " + getMachine() +
                    " Details: " + getMachine().getMachineDetails().getOsDetails());
        }

        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        List<String> commands = Lists.newArrayList();

        commands.addAll(BashCommands.commandsToDownloadUrlsAs(urls, saveAs));
        commands.add(BashCommands.INSTALL_TAR);
        commands.add(String.format("tar xvzf %s", saveAs));

        newScript(INSTALLING)
                .body.append(commands)
                .failIfBodyEmpty()
                .failOnNonZeroResultCode()
                .execute();
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING).execute();

        // Set flag to indicate server has been installed
        entity.setAttribute(EtcdNode.ETCD_NODE_INSTALLED, Boolean.TRUE);
    }

    @Override
    public void launch() {
        DynamicTasks.queueIfPossible(DependentConfiguration.attributeWhenReady(entity, EtcdNode.ETCD_NODE_HAS_JOINED_CLUSTER))
                .orSubmitAndBlock(entity)
                .andWaitForSuccess();

        // Set default values for etcd startup command
        boolean clustered = Optional.fromNullable(entity.getAttribute(DynamicCluster.CLUSTER_MEMBER)).or(false);
        boolean first = Optional.fromNullable(entity.getAttribute(DynamicCluster.FIRST_MEMBER)).or(false);
        String state = (first || !clustered) ? "new" : "existing";
        String nodes = getNodeName() + "=" + getPeerUrl();
        if (clustered) {
            Entity cluster = entity.getAttribute(EtcdCluster.CLUSTER);
            nodes = cluster.getAttribute(EtcdCluster.NODE_LIST);
        }

        // Build etcd startup command
        List<String> commands = Lists.newLinkedList();
        commands.add("cd " + getRunDir());
        commands.add(format("%s -listen-client-urls %s -advertise-client-urls %<s "
                + "-listen-peer-urls %s -initial-advertise-peer-urls %<s "
                + "-initial-cluster-token %s -name %s -initial-cluster-state %s "
                + "-initial-cluster %s "
                + "> %s 2>&1 < /dev/null &",
                Os.mergePathsUnix(getExpandedInstallDir(), "etcd"),
                getClientUrl(), getPeerUrl(), getClusterToken(), getNodeName(), state, nodes,
                getLogFileLocation()));

        newScript(ImmutableMap.of(USE_PID_FILE, true), LAUNCHING)
                .body.append(commands)
                .failOnNonZeroResultCode()
                .execute();
    }

    @Override
    public void stop() {
        leaveCluster(getNodeName());

        newScript(ImmutableMap.of(USE_PID_FILE, true), STOPPING).execute();
    }

    @Override
    public boolean isRunning() {
        return newScript(ImmutableMap.of(USE_PID_FILE, true), CHECK_RUNNING).execute() == 0;
    }

    protected String getEtcdCtlCommand() {
        return Os.mergePathsUnix(getExpandedInstallDir(), "etcdctl");
    }

    protected String getClientUrl() {
        return String.format("http://%s:%d", getHostname(), getEntity().getAttribute(EtcdNode.ETCD_CLIENT_PORT));
    }

    protected String getPeerUrl() {
        return String.format("http://%s:%d", getHostname(), getEntity().getAttribute(EtcdNode.ETCD_PEER_PORT));
    }

    protected String getLogFileLocation() {
        return Os.mergePathsUnix(getRunDir(), "console.log");
    }

    protected String getNodeName() {
        return getEntity().getAttribute(EtcdNode.ETCD_NODE_NAME);
    }

    protected String getClusterToken() {
        return entity.config().get(EtcdCluster.CLUSTER_TOKEN);
    }

    @Override
    public void joinCluster(String nodeName, String nodeAddress) {
        List<String> commands = Lists.newLinkedList();
        commands.add("cd " + getRunDir());
        commands.add(String.format("%s --peers %s member add %s %s", getEtcdCtlCommand(), getClientUrl(), nodeName, nodeAddress));
        newScript("joinCluster")
                .body.append(commands)
                .failOnNonZeroResultCode()
                .execute();
    }

    @Override
    public void leaveCluster(String nodeName) {
        List<String> listMembersCommands = Lists.newLinkedList();
        listMembersCommands.add("cd " + getRunDir());
        listMembersCommands.add(String.format("%s --peers %s member list", getEtcdCtlCommand(), getClientUrl()));
        ScriptHelper listMembersScript = newScript("listMembers")
                .body.append(listMembersCommands)
                .failOnNonZeroResultCode()
                .gatherOutput();
        listMembersScript.execute();

        String output = listMembersScript.getResultStdout();
        Optional<String> found = Iterables.tryFind(Splitter.on(CharMatcher.anyOf("\r\n")).split(output), Predicates.containsPattern("name=" + nodeName));
        if (found.isPresent()) {
            String nodeId = Strings.getFirstWord(found.get()).replace(":", "");

            List<String> removeMemberCommands = Lists.newLinkedList();
            removeMemberCommands.add("cd " + getRunDir());
            removeMemberCommands.add(String.format("%s --peers %s member remove %s", getEtcdCtlCommand(), getClientUrl(), nodeId));
            newScript("removeMember")
                    .body.append(removeMemberCommands)
                    .failOnNonZeroResultCode()
                    .execute();
        }
    }

}
