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
package brooklyn.mesos.example;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.catalog.CatalogConfig;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.location.dynamic.LocationOwner;

import brooklyn.entity.mesos.MesosCluster;
import brooklyn.entity.mesos.framework.marathon.MarathonFramework;

/**
 * Brooklyn managed Mesos cluster with Marathon framework
 */
@Catalog(name = "Mesos and Marathon Cluster",
        description = "A Mesos cluster with the Marathon framework",
        iconUrl = "classpath://marathon-logo.jpg")
@ImplementedBy(ExternalMesosWithMarathonImpl.class)
public interface ExternalMesosWithMarathon extends Application {

    @CatalogConfig(label = "Location Name", priority = 90)
    ConfigKey<String> LOCATION_NAME = ConfigKeys.newConfigKeyWithDefault(LocationOwner.LOCATION_NAME.getConfigKey(), "my-mesos-cluster");

    @CatalogConfig(label = "Mesos URL", priority = 90)
    ConfigKey<String> MESOS_URL = MesosCluster.MESOS_URL;

    @CatalogConfig(label = "Marathon URL", priority = 80)
    ConfigKey<String> MARATHON_URL = MarathonFramework.MARATHON_URL;

}
