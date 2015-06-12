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

import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.os.Os;
import brooklyn.util.task.DynamicTasks;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

public class EtcdProxySshDriver extends EtcdNodeSshDriver implements EtcdProxyDriver {

    public EtcdProxySshDriver(final EtcdNodeImpl entity, final SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public EtcdNodeImpl getEntity() {
        return EtcdProxyImpl.class.cast(super.getEntity());
    }

    protected Map<String, Integer> getPortMap() {
        return MutableMap.of("clientPort", getEntity().getAttribute(EtcdProxy.ETCD_CLIENT_PORT));
    }

    @Override
    public void launch() {
        DynamicTasks.queueIfPossible(DependentConfiguration.attributeWhenReady(entity, EtcdNode.ETCD_NODE_HAS_JOINED_CLUSTER))
                .orSubmitAndBlock(entity)
                .andWaitForSuccess();

        String nodes = String.format("%s=%s", getClusterName(), getClusterUrl());

        // Build etcd startup command
        List<String> commands = Lists.newLinkedList();
        commands.add("cd " + getRunDir());
        commands.add(format("%s -bind-addr 0.0.0.0:%d --proxy on --initial-cluster \"%s\" > %s 2>&1 < /dev/null &",
                        Os.mergePathsUnix(getExpandedInstallDir(), "etcd"),
                        getEntity().getAttribute(EtcdNode.ETCD_CLIENT_PORT),
                        nodes,
                        getLogFileLocation()));

        newScript(ImmutableMap.of(USE_PID_FILE, true), LAUNCHING)
                .body.append(commands)
                .failOnNonZeroResultCode()
                .execute();
    }

    protected String getClusterName() {
        return entity.config().get(EtcdProxy.ETCD_CLUSTER_NAME);
    }

    protected String getClusterUrl() {
        return entity.config().get(EtcdProxy.ETCD_CLUSTER_URL);
    }

    @Override
    public void joinCluster(String nodeName, String nodeAddress) {
    }

    @Override
    public void leaveCluster(String nodeName) {
    }

}
