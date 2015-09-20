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
package brooklyn.entity.mesos;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.catalog.CatalogConfig;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.location.dynamic.LocationOwner;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.group.DynamicGroup;
import org.apache.brooklyn.entity.group.DynamicMultiGroup;
import org.apache.brooklyn.entity.stock.BasicStartable;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.time.Duration;

import brooklyn.entity.mesos.framework.MesosFramework;
import brooklyn.entity.mesos.framework.marathon.MarathonFramework;
import brooklyn.location.mesos.MesosLocation;

/**
 * A Mesos cluster entity.
 */
@Catalog(name = "Mesos Cluster",
        description = "Mesos is an open-source distributed operating system kernel.",
        iconUrl = "classpath:///mesos-logo.png")
@ImplementedBy(MesosClusterImpl.class)
public interface MesosCluster extends BasicStartable, LocationOwner<MesosLocation, MesosCluster> {

    @CatalogConfig(label = "Location Name", priority = 90)
    @SetFromFlag("locationName")
    ConfigKey<String> LOCATION_NAME = ConfigKeys.newConfigKeyWithDefault(LocationOwner.LOCATION_NAME.getConfigKey(), "my-mesos-cluster");

    @CatalogConfig(label = "Mesos URL", priority = 50)
    @SetFromFlag("mesosUrl")
    ConfigKey<String> MESOS_URL = ConfigKeys.newStringConfigKey("mesos.url", "Mesos URL", "http://localhost:5050/");

    @SetFromFlag("shutdownTimeout")
    ConfigKey<Duration> SHUTDOWN_TIMEOUT = ConfigKeys.newDurationConfigKey("mesos.timeout.shutdown", "Timeout to wait for children when shutting down", Duration.FIVE_MINUTES);

    AttributeSensor<DynamicGroup> MESOS_FRAMEWORKS = Sensors.newSensor(DynamicGroup.class, "mesos.frameworks", "Mesos frameworks");
    AttributeSensor<DynamicGroup> MESOS_TASKS = Sensors.newSensor(DynamicGroup.class, "mesos.tasks", "Mesos tasks");
    AttributeSensor<DynamicMultiGroup> MESOS_APPLICATIONS = Sensors.newSensor(DynamicMultiGroup.class, "mesos.applications", "Mesos applications");

    AttributeSensor<String> CLUSTER_NAME = Sensors.newStringSensor("mesos.cluster.name", "Mesos cluster name");
    AttributeSensor<String> CLUSTER_ID = Sensors.newStringSensor("mesos.cluster.id", "Mesos cluster ID");
    AttributeSensor<String> MESOS_VERSION = Sensors.newStringSensor("mesos.version", "Mesos version");
    AttributeSensor<Integer> CPUS_TOTAL = Sensors.newIntegerSensor("mesos.cpus.total", "Total number of available CPUs");
    AttributeSensor<Long> MEMORY_FREE_BYTES = Sensors.newLongSensor("mesos.memory.free", "Free system memory in bytes");
    AttributeSensor<Long> MEMORY_TOTAL_BYTES = Sensors.newLongSensor("mesos.memory.total", "Total system memory in bytes");
    AttributeSensor<Double> LOAD_1MIN = Sensors.newDoubleSensor("mesos.load.1min", "Average system load for last minute in uptime(1) style");
    AttributeSensor<Double> LOAD_5MIN = Sensors.newDoubleSensor("mesos.load.5min", "Average system load for last 5 minutes in uptime(1) style");
    AttributeSensor<Double> LOAD_15MIN = Sensors.newDoubleSensor("mesos.load.15min", "Average system load for last 15 minutes in uptime(1) style");

    @SetFromFlag("scanInterval")
    ConfigKey<Duration> SCAN_INTERVAL = ConfigKeys.newConfigKey(Duration.class,
            "mesos.scanInterval", "Interval between scans of Mesos tasks and frameworks", Duration.TEN_SECONDS);

    AttributeSensor<Void> MESOS_FRAMEWORK_SCAN = Sensors.newSensor(Void.class, "mesos.frameworks.scan", "Notification of framework scan");
    AttributeSensor<List<String>> MESOS_FRAMEWORK_LIST = Sensors.newSensor(new TypeToken<List<String>>() { }, "mesos.frameworks.list", "List of Mesos frameworks");

    Map<String, EntitySpec<? extends MesosFramework>> FRAMEWORKS = ImmutableMap.<String, EntitySpec<? extends MesosFramework>>builder()
            .put("marathon", EntitySpec.create(MarathonFramework.class))
            .put("aurora", EntitySpec.create(MarathonFramework.class))
//            .put("elasticsearch", EntitySpec.create(ElasticSearchFramework.class))
            .build();

}
