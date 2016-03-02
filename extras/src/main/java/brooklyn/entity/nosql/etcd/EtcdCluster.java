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
package brooklyn.entity.nosql.etcd;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.reflect.TypeToken;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

@Catalog(name="Etcd Cluster", description="Etcd is an open-source distributed key-value store that serves as "
        + "the backbone of distributed systems by providing a canonical hub for cluster coordination and state management.")
@ImplementedBy(EtcdClusterImpl.class)
public interface EtcdCluster extends DynamicCluster {

    @SuppressWarnings("serial")
    AttributeSensor<Map<Entity, String>> ETCD_CLUSTER_NODES = Sensors.newSensor(
            new TypeToken<Map<Entity, String>>() {}, 
            "etcd.cluster.nodes", "Names of all active etcd nodes in the cluster (entity reference to name mapping)");

    @SetFromFlag("clusterName")
    ConfigKey<String> CLUSTER_NAME = ConfigKeys.newStringConfigKey("etcd.cluster.name", "The Etcd cluster name", "brooklyn");

    @SetFromFlag("clusterToken")
    ConfigKey<String> CLUSTER_TOKEN = ConfigKeys.newStringConfigKey("etcd.cluster.token", "The Etcd cluster token", "brooklyn");

    @SetFromFlag("etcdNodeSpec")
    AttributeSensorAndConfigKey<EntitySpec<?>,EntitySpec<?>> ETCD_NODE_SPEC = ConfigKeys.newSensorAndConfigKey(
            new TypeToken<EntitySpec<?>>() { }, "etcd.node.spec", "Etcd node specification",
            EntitySpec.create(EtcdNode.class));

    AttributeSensor<AtomicInteger> NODE_ID = Sensors.newSensor(AtomicInteger.class, "etcd.cluster.nodeId", "Counter for generating node IDs");

    AttributeSensor<Boolean> IS_FIRST_NODE_SET = Sensors.newBooleanSensor("etcd.cluster.isFirstNodeSet", "Flag to determine if the first node has been set");

    AttributeSensor<Entity> FIRST_NODE = Sensors.newSensor(Entity.class, "etcd.cluster.firstNode", "The first node in the cluster");

    AttributeSensor<String> NODE_LIST = Sensors.newStringSensor("etcd.cluster.nodeList", "List of nodes (including ports), comma separated");

    Object getClusterMutex();

}
