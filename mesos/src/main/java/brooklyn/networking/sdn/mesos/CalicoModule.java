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
package brooklyn.networking.sdn.mesos;

import java.net.InetAddress;

import clocker.docker.networking.entity.sdn.SdnProvider;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;

import brooklyn.entity.mesos.MesosAttributes;
import brooklyn.entity.mesos.MesosCluster;
import brooklyn.entity.mesos.MesosSlave;
import brooklyn.entity.nosql.etcd.EtcdProxy;

/**
 * The Calico NetworkModule for Mesos.
 */
@ImplementedBy(CalicoModuleImpl.class)
public interface CalicoModule extends SdnProvider {

    AttributeSensorAndConfigKey<Entity, Entity> MESOS_CLUSTER = MesosAttributes.MESOS_CLUSTER;

    AttributeSensorAndConfigKey<String, String> ETCD_CLUSTER_URL = EtcdProxy.ETCD_CLUSTER_URL;

    ConfigKey<Boolean> USE_IPIP = ConfigKeys.newBooleanConfigKey("calico.ipip", "Use the IPIP protocol for inter-VM traffic", Boolean.FALSE);
    ConfigKey<Boolean> USE_NAT = ConfigKeys.newBooleanConfigKey("calico.nat", "Use NAT for outgoing traffic", Boolean.FALSE);

    MesosCluster getMesosCluster();

    String execCalicoCommand(MesosSlave slave, String command);

    InetAddress attachNetwork(MesosSlave slave, Entity entity, String containerId, String subnetId);

}
