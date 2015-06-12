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

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.location.basic.PortRanges;
import brooklyn.util.flags.SetFromFlag;

@Catalog(name="Etcd Node")
@ImplementedBy(EtcdNodeImpl.class)
public interface EtcdNode extends SoftwareProcess {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "2.0.11");

    @SetFromFlag("downloadUrl")
    ConfigKey<String> DOWNLOAD_URL = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.DOWNLOAD_URL.getConfigKey(),
            "https://github.com/coreos/etcd/releases/download/v${version}/etcd-v${version}-linux-amd64.tar.gz");

    @SetFromFlag("etcdClientPort")
    PortAttributeSensorAndConfigKey ETCD_CLIENT_PORT = new PortAttributeSensorAndConfigKey("etcd.port.client", "Etcd client port", PortRanges.fromInteger(2379));

    @SetFromFlag("etcdPeerPort")
    PortAttributeSensorAndConfigKey ETCD_PEER_PORT = new PortAttributeSensorAndConfigKey("etcd.port.peer", "Etcd peer port", PortRanges.fromInteger(2380));

    @SetFromFlag("nodeName")
    AttributeSensorAndConfigKey<String, String> ETCD_NODE_NAME = ConfigKeys.newStringSensorAndConfigKey(
            "etcd.node.name", "Returns the Etcd node name");

    AttributeSensor<Boolean> ETCD_NODE_INSTALLED = Sensors.newBooleanSensor("etcd.node.installed", "Set when the etcd software has been installed");

    AttributeSensor<Entity> ETCD_CLUSTER = Sensors.newSensor(Entity.class, "etcd.cluster", "Returns the Etcd cluster entity");

    AttributeSensor<Boolean> ETCD_NODE_HAS_JOINED_CLUSTER = Sensors.newBooleanSensor(
            "etcd.node.nodeHasJoinedCluster", "Flag to indicate whether the etcd node has joined a cluster member");
 
    MethodEffector<Void> JOIN_ETCD_CLUSTER = new MethodEffector<Void>(EtcdNode.class, "joinCluster");
    MethodEffector<Void> LEAVE_ETCD_CLUSTER = new MethodEffector<Void>(EtcdNode.class, "leaveCluster");

    @Effector(description = "Add this etcd node to the etcd cluster")
    public void joinCluster(@EffectorParam(name = "nodeName") String nodeName, @EffectorParam(name = "nodeAddress") String nodeAddress);

    @Effector(description = "Remove this etcd node from the cluster")
    public void leaveCluster(@EffectorParam(name = "nodeName") String nodeName);

    public boolean hasJoinedCluster();

}
