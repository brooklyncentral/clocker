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
package brooklyn.entity.mesos.task.marathon;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.catalog.CatalogConfig;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import brooklyn.entity.mesos.task.MesosTask;

/**
 * A Marathon task.
 */
@Catalog(name = "Marathon Task",
        description = "A Marathon application task running a Docker image.",
        iconUrl = "classpath:///marathon-logo.jpg")
@ImplementedBy(MarathonTaskImpl.class)
public interface MarathonTask extends MesosTask {

    @CatalogConfig(label = "Application ID", priority = 50)
    @SetFromFlag("id")
    ConfigKey<String> APPLICATION_ID = ConfigKeys.newStringConfigKey("marathon.task.id", "Marathon task application id");

    @CatalogConfig(label = "Command", priority = 50)
    @SetFromFlag("command")
    ConfigKey<String> COMMAND = ConfigKeys.newStringConfigKey("marathon.task.command", "Marathon task command string");

    @CatalogConfig(label = "Docker Image", priority = 50)
    @SetFromFlag("imageName")
    ConfigKey<String> DOCKER_IMAGE_NAME = ConfigKeys.newStringConfigKey("marathon.task.imageName", "Marathon task Docker image");

    @CatalogConfig(label = "Docker Image Version", priority = 50)
    @SetFromFlag("imageVersion")
    ConfigKey<String> DOCKER_IMAGE_TAG = ConfigKeys.newStringConfigKey("marathon.task.imageVersion", "Marathon task Docker image version");

    // TODO environment and port specification
    // TODO cpu and memory constraints
    // TODO labels

}
