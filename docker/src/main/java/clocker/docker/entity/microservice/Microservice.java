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
package clocker.docker.entity.microservice;

import java.util.Map;

import clocker.docker.entity.DockerHost;
import clocker.docker.entity.container.DockerContainer;
import clocker.docker.entity.container.application.VanillaDockerApplication;
import clocker.docker.entity.util.DockerAttributes;

import org.apache.brooklyn.api.catalog.CatalogConfig;
import org.apache.brooklyn.core.entity.StartableApplication;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

/**
 * Brooklyn managed {@link VanillaDockerApplication}.
 */
@ImplementedBy(MicroserviceImageImpl.class)
public interface Microservice extends StartableApplication {

    String DOCKER_LOCATION_PREFIX = "docker-";

    @CatalogConfig(label = "Container Name", priority = 90)
    @SetFromFlag("containerName")
    ConfigKey<String> CONTAINER_NAME = DockerContainer.DOCKER_CONTAINER_NAME.getConfigKey();

    @CatalogConfig(label = "Open Ports", priority = 70)
    @SetFromFlag("openPorts")
    ConfigKey<String> OPEN_PORTS = ConfigKeys.newStringConfigKey("docker.openPorts", "Comma separated list of ports the application uses");

    @CatalogConfig(label = "Direct Ports", priority = 70)
    @SetFromFlag("directPorts")
    ConfigKey<String> DIRECT_PORTS = ConfigKeys.newStringConfigKey("docker.directPorts", "Comma separated list of ports to open directly on the host");

    @SetFromFlag("portBindings")
    ConfigKey<Map<Integer, Integer>> PORT_BINDINGS = DockerAttributes.DOCKER_PORT_BINDINGS;

    @SetFromFlag("env")
    ConfigKey<Map<String, Object>> DOCKER_CONTAINER_ENVIRONMENT = DockerContainer.DOCKER_CONTAINER_ENVIRONMENT.getConfigKey();

    @SetFromFlag("volumeMappings")
    ConfigKey<Map<String, String>> DOCKER_HOST_VOLUME_MAPPING = DockerHost.DOCKER_HOST_VOLUME_MAPPING.getConfigKey();

    ConfigKey<String> ONBOX_BASE_DIR = ConfigKeys.newConfigKeyWithDefault(BrooklynConfigKeys.ONBOX_BASE_DIR, "/tmp/brooklyn");
    ConfigKey<Boolean> SKIP_ON_BOX_BASE_DIR_RESOLUTION = ConfigKeys.newConfigKeyWithDefault(BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION, Boolean.TRUE);

}
