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
package brooklyn.networking.sdn.calico;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import brooklyn.entity.nosql.etcd.EtcdCluster;
import brooklyn.networking.sdn.DockerSdnProvider;

/**
 * A collection of machines running Calico.
 */
@Catalog(name = "Calico Infrastructure", description = "Calico SDN", iconUrl = "classpath://calico-logo.png")
@ImplementedBy(CalicoNetworkImpl.class)
public interface CalicoNetwork extends DockerSdnProvider {

    @SetFromFlag("calicoVersion")
    ConfigKey<String> CALICO_VERSION = ConfigKeys.newStringConfigKey("calico.version", "The Calico SDN version number", "0.4.9");

    ConfigKey<EntitySpec<?>> CALICO_NODE_SPEC = ConfigKeys.newConfigKeyWithDefault(SDN_AGENT_SPEC.getConfigKey(), EntitySpec.create(CalicoNode.class));

    @SetFromFlag("etcdVersion")
    ConfigKey<String> ETCD_VERSION = ConfigKeys.newStringConfigKey("etcd.version", "The Etcd version number", "2.0.11");

    ConfigKey<Boolean> EXTERNAL_ETCD_CLUSTER = ConfigKeys.newBooleanConfigKey("calico.etcd.external", "Whether to use an external Etcd cluster", Boolean.FALSE);
    ConfigKey<Integer> EXTERNAL_ETCD_INITIAL_SIZE = ConfigKeys.newIntegerConfigKey("calico.etcd.external.initialSize", "The initial size of the external Etcd cluster");
    AttributeSensorAndConfigKey<String, String> EXTERNAL_ETCD_URL = ConfigKeys.newStringSensorAndConfigKey("calico.etcd.external.url", "The URL for the external Etcd cluster (if configured, no cluster will be provisioned)");

    AttributeSensor<EtcdCluster> ETCD_CLUSTER = Sensors.newSensor(EtcdCluster.class, "etcd.cluster", "The EtcdCluster entity for storing state");

}
