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
package org.apache.brooklyn.entity.nosql.etcd;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.annotation.Effector;
import org.apache.brooklyn.core.annotation.EffectorParam;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.effector.MethodEffector;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.BasicAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.time.Duration;

@Catalog(name="Etcd Node")
@ImplementedBy(EtcdNodeImpl.class)
public interface EtcdNode extends SoftwareProcess {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "2.3.1");

    @SetFromFlag("startTimeout")
    ConfigKey<Duration> START_TIMEOUT = ConfigKeys.newConfigKeyWithDefault(BrooklynConfigKeys.START_TIMEOUT, Duration.minutes(10));

    @SetFromFlag("downloadUrl")
    AttributeSensorAndConfigKey<String, String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "https://github.com/coreos/etcd/releases/download/v${version}/etcd-v${version}-linux-amd64.tar.gz");

    @SetFromFlag("archiveNameFormat")
    ConfigKey<String> ARCHIVE_DIRECTORY_NAME_FORMAT = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.ARCHIVE_DIRECTORY_NAME_FORMAT, "etcd-v%s-linux-amd64");

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
    void joinCluster(@EffectorParam(name = "nodeName") String nodeName, @EffectorParam(name = "nodeAddress") String nodeAddress);

    @Effector(description = "Remove this etcd node from the cluster")
    void leaveCluster(@EffectorParam(name = "nodeName") String nodeName);

    boolean hasJoinedCluster();

    Object getClusterMutex();

}
