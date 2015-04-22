/*
 * Copyright 2014-2015 by Cloudsoft Corporation Limited
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

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.nosql.etcd.EtcdNode;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.networking.sdn.SdnAgent;
import brooklyn.util.flags.SetFromFlag;

/**
 * The Calico plugin
 */
@ImplementedBy(CalicoNodeImpl.class)
public interface CalicoNode extends SdnAgent {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "0.1.0");

    @SetFromFlag("downloadUrl")
    ConfigKey<String> DOWNLOAD_URL = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.DOWNLOAD_URL.getConfigKey(),
            "https://github.com/Metaswitch/calico-docker/releases/download/v${version}/calicoctl");

    @SetFromFlag("etcdNode")
    AttributeSensorAndConfigKey<EtcdNode, EtcdNode> ETCD_NODE = ConfigKeys.newSensorAndConfigKey(EtcdNode.class,
            "sdn.calico.etcd.node", "The EtcdNode attached to the same DockerHost as the plugin");

    @SetFromFlag("powerstripPort")
    ConfigKey<Integer> POWERSTRIP_PORT = ConfigKeys.newIntegerConfigKey("powerstrip.port", "Powerstrip port", 2377);


}
