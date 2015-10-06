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

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.google.common.reflect.TypeToken;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.annotation.Effector;
import org.apache.brooklyn.core.annotation.EffectorParam;
import org.apache.brooklyn.core.effector.MethodEffector;
import org.apache.brooklyn.core.sensor.Sensors;

import brooklyn.entity.mesos.framework.MesosFramework;

/**
 * The Marathon framework for Mesos.
 */
@Catalog(name = "Marathon Framework",
        description = "Marathon is an open-source PaaS framework for Mesos.")
@ImplementedBy(MarathonFrameworkImpl.class)
public interface MarathonFramework extends MesosFramework {

    AttributeSensor<List<String>> MARATHON_APPLICATIONS = Sensors.newSensor(new TypeToken<List<String>>() { }, "marathon.applications", "List of Marathon applications");

    AttributeSensor<String> MARATHON_VERSION = Sensors.newStringSensor("marathon.version", "Marathon version");

    AttributeSensor<String> MARATHON_LEADER_URI = Sensors.newStringSensor("marathon.leader.uri", "Marathon leader URI");

    // Effectors

    MethodEffector<Void> START_APPLICATION = new MethodEffector<Void>(MarathonFramework.class, "startApplication");

    /**
     * Start a Marathon application.
     */
    @Effector(description="Start a Marathon application")
    boolean startApplication(
            @EffectorParam(name="id", description="Application ID") String id,
            @EffectorParam(name="flags", description="Task flags") Map<String, Object> flags) throws IOException;

}
