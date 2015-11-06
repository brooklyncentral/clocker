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
package brooklyn.entity.mesos.framework.marathon;

import java.util.List;
import java.util.Map;

import com.google.common.reflect.TypeToken;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.annotation.Effector;
import org.apache.brooklyn.core.annotation.EffectorParam;
import org.apache.brooklyn.core.effector.MethodEffector;
import org.apache.brooklyn.core.location.dynamic.LocationOwner;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.group.DynamicCluster;

import brooklyn.entity.mesos.framework.MesosFramework;
import brooklyn.location.mesos.framework.marathon.MarathonLocation;

/**
 * The Marathon framework for Mesos.
 */
@Catalog(name = "Marathon Framework",
        description = "Marathon is an open-source PaaS framework for Mesos.")
@ImplementedBy(MarathonFrameworkImpl.class)
public interface MarathonFramework extends MesosFramework, LocationOwner<MarathonLocation, MarathonFramework> {

    AttributeSensor<DynamicCluster> MARATHON_TASK_CLUSTER = Sensors.newSensor(DynamicCluster.class,
            "marathon.task.cluster", "The Marathon tasks started by Clocker");

    AttributeSensor<List<String>> MARATHON_APPLICATIONS = Sensors.newSensor(new TypeToken<List<String>>() { }, "marathon.applications", "List of Marathon applications");

    AttributeSensor<String> MARATHON_VERSION = Sensors.newStringSensor("marathon.version", "Marathon version");

    AttributeSensor<String> MARATHON_LEADER_URI = Sensors.newStringSensor("marathon.leader.uri", "Marathon leader URI");

//    AttributeSensor<SubnetTier> MARATHON_SUBNET_TIER = Sensors.newSensor(SubnetTier.class,
//            "marathon.subnetTier", "The SubnetTier for Marathon port mapping");

    // Effectors

    MethodEffector<Void> START_APPLICATION = new MethodEffector<Void>(MarathonFramework.class, "startApplication");
    MethodEffector<Void> STOP_APPLICATION = new MethodEffector<Void>(MarathonFramework.class, "stopApplication");

    /**
     * Start a Marathon application.
     */
    @Effector(description="Start a Marathon application")
    String startApplication(
            @EffectorParam(name="id", description="Application ID") String id,
            @EffectorParam(name="flags", description="Task flags") Map<String, Object> flags);

    /**
     * Stop a Marathon application.
     */
    @Effector(description="Stop a Marathon application")
    String stopApplication(
            @EffectorParam(name="id", description="Application ID") String id);

    // Methods

    DynamicCluster getTaskCluster();

}
