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
package brooklyn.clocker.example;

import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.catalog.CatalogConfig;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import brooklyn.entity.container.docker.application.VanillaDockerApplication;

/**
 * Brooklyn managed {@link VanillaDockerApplication}.
 */
public interface Microservice extends VanillaDockerApplication {

    @CatalogConfig(label = "Container Name", priority = 90)
    @SetFromFlag("containerName")
    ConfigKey<String> CONTAINER_NAME = VanillaDockerApplication.CONTAINER_NAME;

    @CatalogConfig(label = "Open Ports", priority = 70)
    @SetFromFlag("openPorts")
    ConfigKey<List<Integer>> OPEN_PORTS = VanillaDockerApplication.DOCKER_OPEN_PORTS;

    @CatalogConfig(label = "Direct Ports", priority = 70)
    @SetFromFlag("directPorts")
    ConfigKey<List<Integer>> DIRECT_PORTS = VanillaDockerApplication.DOCKER_DIRECT_PORTS;

    ConfigKey<String> ONBOX_BASE_DIR = ConfigKeys.newConfigKeyWithDefault(BrooklynConfigKeys.ONBOX_BASE_DIR, "/tmp/brooklyn");
    ConfigKey<Boolean> SKIP_ON_BOX_BASE_DIR_RESOLUTION = ConfigKeys.newConfigKeyWithDefault(BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION, Boolean.TRUE);

}
