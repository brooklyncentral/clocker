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

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.util.text.Strings;

public class EtcdNodeImpl extends SoftwareProcessImpl implements EtcdNode {

    private static final Logger LOG = LoggerFactory.getLogger(EtcdNode.class);

    public void init() {
       super.init();

       String nodeName = config().get(ETCD_NODE_NAME);
       Entity cluster = getParent();

       if (cluster instanceof EtcdCluster) {
           setAttribute(ETCD_CLUSTER, cluster);
           String clusterName = cluster.config().get(EtcdCluster.CLUSTER_NAME);
           if (Strings.isBlank(nodeName)) nodeName = clusterName;
           AtomicInteger nodeId = cluster.getAttribute(EtcdCluster.NODE_ID);
           nodeName += nodeId.incrementAndGet();
       } else {
           setAttribute(EtcdNode.ETCD_NODE_HAS_JOINED_CLUSTER, Boolean.TRUE);
       }

       setAttribute(ETCD_NODE_NAME, Strings.isBlank(nodeName) ? getId() : nodeName);
       LOG.info("Starting {} node: {}", cluster instanceof EtcdCluster ? "clustered" : "single", getNodeName());
    }

    protected String getNodeName() {
        return getAttribute(EtcdNode.ETCD_NODE_NAME);
    }

    @Override
    public EtcdNodeDriver getDriver() {
        return (EtcdNodeDriver) super.getDriver();
    }

    @Override
    public Class<EtcdNodeDriver> getDriverInterface() {
        return EtcdNodeDriver.class;
    }

    @Override
    public void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();
    }

    public void disconnectSensors() {
        disconnectServiceUpIsRunning();
        super.disconnectSensors();
    }

    @Override
    public void joinCluster(String nodeName, String nodeAddress) {
        getDriver().joinCluster(nodeName, nodeAddress);
    }

    @Override
    public void leaveCluster(String nodeName) {
        getDriver().leaveCluster(nodeName);
    }

    @Override
    public boolean hasJoinedCluster() {
        return Boolean.TRUE.equals(getAttribute(EtcdNode.ETCD_NODE_HAS_JOINED_CLUSTER));
    }

}
