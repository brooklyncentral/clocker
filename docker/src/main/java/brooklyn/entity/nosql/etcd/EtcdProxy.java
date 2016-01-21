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

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

@Catalog(name="Etcd Proxy")
@ImplementedBy(EtcdProxyImpl.class)
public interface EtcdProxy extends EtcdNode {

    @SetFromFlag("etcdClientPort")
    PortAttributeSensorAndConfigKey ETCD_CLIENT_PORT = new PortAttributeSensorAndConfigKey("etcd.port.client", "Etcd proxy client port", PortRanges.fromInteger(4001));

    AttributeSensorAndConfigKey<String, String> ETCD_CLUSTER_URL = ConfigKeys.newStringSensorAndConfigKey("etcd.cluster.url", "Returns the Etcd cluster URL");
    AttributeSensorAndConfigKey<String, String> ETCD_CLUSTER_NAME = ConfigKeys.newStringSensorAndConfigKey("etcd.cluster.name", "Returns the Etcd cluster name");

}
