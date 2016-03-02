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
package clocker.mesos.entity;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.machine.MachineEntity;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import brooklyn.networking.subnet.SubnetTier;

/**
 * A Mesos slave machine
 */
@ImplementedBy(MesosSlaveImpl.class)
public interface MesosSlave extends MachineEntity {

    @SetFromFlag("slaveAccessible")
    ConfigKey<Boolean> SLAVE_ACCESSIBLE = ConfigKeys.newBooleanConfigKey("mesos.slave.accessible", "Try to connect to Mesos slave over SSH", Boolean.FALSE);

    ConfigKey<String> SLAVE_SSH_USER = ConfigKeys.newStringConfigKey("mesos.slave.ssh.user", "Username for SSH access to Mesos slaves", "root");
    ConfigKey<String> SLAVE_SSH_PASSWORD = ConfigKeys.newStringConfigKey("mesos.slave.ssh.password", "Password for SSH access to Mesos slaves");
    ConfigKey<Integer> SLAVE_SSH_PORT = ConfigKeys.newIntegerConfigKey("mesos.slave.ssh.port", "Port for SSH access to Mesos slaves", 22);
    ConfigKey<String> SLAVE_SSH_PRIVATE_KEY_FILE = ConfigKeys.newStringConfigKey("mesos.slave.ssh.privateKey.file", "Private key file for SSH access to Mesos slaves");
    ConfigKey<String> SLAVE_SSH_PRIVATE_KEY_DATA = ConfigKeys.newStringConfigKey("mesos.slave.ssh.privateKey.data", "Private key data for SSH access to Mesos slaves");

    AttributeSensor<Boolean> SLAVE_ACTIVE = Sensors.newBooleanSensor("mesos.slave.active", "Mesos slave currently active");

    AttributeSensorAndConfigKey<Entity, Entity> MESOS_CLUSTER = MesosAttributes.MESOS_CLUSTER;

    AttributeSensorAndConfigKey<String, String> MESOS_SLAVE_ID = ConfigKeys.newSensorAndConfigKey(String.class, "mesos.slave.id", "Mesos slave ID");

    AttributeSensorAndConfigKey<Long, Long> REGISTERED_AT = ConfigKeys.newSensorAndConfigKey(Long.class, "mesos.slave.registered", "Time slave was registered");

    AttributeSensor<Long> MEMORY_AVAILABLE = Sensors.newLongSensor("mesos.slave.resources.memory", "Memory resource available");
    AttributeSensor<Double> CPU_AVAILABLE = Sensors.newDoubleSensor("mesos.slave.resources.cpu", "CPU resource available");
    AttributeSensor<Long> DISK_AVAILABLE = Sensors.newLongSensor("mesos.slave.resources.disk", "Disk resource available");

    AttributeSensor<Long> MEMORY_USED = Sensors.newLongSensor("mesos.slave.used.memory", "Memory resource used");
    AttributeSensor<Double> CPU_USED = Sensors.newDoubleSensor("mesos.slave.used.cpu", "CPU resource used");
    AttributeSensor<Long> DISK_USED = Sensors.newLongSensor("mesos.slave.used.disk", "Disk resource used");

    AttributeSensor<SubnetTier> SUBNET_TIER = Sensors.newSensor(SubnetTier.class,
            "mesos.slave.subnetTier", "The SubnetTier for port mapping");

    ConfigKey<Boolean> SKIP_ENTITY_START = ConfigKeys.newConfigKeyWithDefault(BrooklynConfigKeys.SKIP_ENTITY_START, Boolean.TRUE);
    ConfigKey<Boolean> SKIP_ON_BOX_BASE_DIR_RESOLUTION = ConfigKeys.newConfigKeyWithDefault(BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION, Boolean.TRUE);

    MesosCluster getMesosCluster();

    SubnetTier getSubnetTier();

}
