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

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.catalog.CatalogConfig;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;

import brooklyn.entity.container.docker.application.VanillaDockerApplication;

/**
 * Brooklyn managed {@link VanillaDockerApplication}.
 */
@Catalog(name = "Dockerfile Micro-Service",
        description = "A container micro-service defined by a Dockerfile")
@ImplementedBy(MicroServiceImpl.MicroServiceDockerfileImpl.class)
public interface MicroServiceDockerfile extends VanillaDockerApplication {

    @CatalogConfig(label = "Container Name", priority = 90)
    ConfigKey<String> CONTAINER_NAME = ConfigKeys.newConfigKeyWithDefault(VanillaDockerApplication.CONTAINER_NAME, "service");

    @CatalogConfig(label = "Dockerfile URL", priority = 80)
    ConfigKey<String> DOCKERFILE_URL = VanillaDockerApplication.DOCKERFILE_URL;

    @CatalogConfig(label = "Open Ports", priority = 70)
    ConfigKey<List<Integer>> OPEN_PORTS = VanillaDockerApplication.DOCKER_OPEN_PORTS;

    @CatalogConfig(label = "Direct Ports", priority = 70)
    ConfigKey<List<Integer>> DIRECT_PORTS = VanillaDockerApplication.DOCKER_DIRECT_PORTS;
}
