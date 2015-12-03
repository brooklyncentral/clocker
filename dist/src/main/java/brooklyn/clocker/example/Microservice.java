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

import brooklyn.entity.container.docker.DockerContainer;
import org.apache.brooklyn.api.catalog.CatalogConfig;
import org.apache.brooklyn.core.entity.StartableApplication;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;

import brooklyn.entity.container.docker.application.VanillaDockerApplication;

import java.util.Map;

/**
 * Brooklyn managed {@link VanillaDockerApplication}.
 */
@ImplementedBy(MicroserviceImageImpl.class)
public interface Microservice extends StartableApplication {

    String DOCKER_LOCATION_PREFIX = "docker-";

    @CatalogConfig(label = "Container Name", priority = 90)
    ConfigKey<String> CONTAINER_NAME = ConfigKeys.newStringConfigKey("docker.containerName", "Container name", "service");

    @CatalogConfig(label = "Open Ports", priority = 70)
    ConfigKey<String> OPEN_PORTS = ConfigKeys.newStringConfigKey("docker.openPorts", "Comma separated list of ports the application uses");

    @CatalogConfig(label = "Direct Ports", priority = 70)
    ConfigKey<String> DIRECT_PORTS = ConfigKeys.newStringConfigKey("docker.directPorts", "Comma separated list of ports to open directly on the host");

    @CatalogConfig(label = "Container Environment Variables", priority = 70)
    ConfigKey<Map<String, Object>> DOCKER_CONTAINER_ENVIRONMENT = DockerContainer.DOCKER_CONTAINER_ENVIRONMENT.getConfigKey();

    ConfigKey<String> ONBOX_BASE_DIR = ConfigKeys.newConfigKeyWithDefault(BrooklynConfigKeys.ONBOX_BASE_DIR, "/tmp/brooklyn");
    ConfigKey<Boolean> SKIP_ON_BOX_BASE_DIR_RESOLUTION = ConfigKeys.newConfigKeyWithDefault(BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION, Boolean.TRUE);

}
