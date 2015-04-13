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

import brooklyn.catalog.Catalog;
import brooklyn.catalog.CatalogConfig;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.container.docker.application.VanillaDockerApplication;
import brooklyn.entity.proxying.EntitySpec;

/**
 * Brooklyn managed {@link VanillaDockerApplication}.
 */
@Catalog(name = "Dockerfile Micro-Service",
        description = "A container micro-service defined by a Dockerfile",
        iconUrl = "classpath://container.png")
public class MicroServiceDockerfile extends AbstractApplication {

    @CatalogConfig(label = "Container Name", priority = 90)
    public static final ConfigKey<String> CONTAINER_NAME = ConfigKeys.newStringConfigKey("docker.containerName", "Container name", "service");

    @CatalogConfig(label = "Dockerfile URL", priority = 80)
    public static final ConfigKey<String> DOCKERFILE_URL = VanillaDockerApplication.DOCKERFILE_URL;

    @CatalogConfig(label = "Open Ports", priority = 70)
    public static final ConfigKey<String> OPEN_PORTS = ConfigKeys.newStringConfigKey("docker.openPorts", "Comma separated list of ports the application uses");

    @CatalogConfig(label = "Direct Ports", priority = 70)
    public static final ConfigKey<String> DIRECT_PORTS = ConfigKeys.newStringConfigKey("docker.directPorts", "Comma separated list of ports to open directly on the host");

    @Override
    public void initApp() {
        addChild(EntitySpec.create(VanillaDockerApplication.class)
                .configure("containerName", config().get(CONTAINER_NAME))
                .configure("dockerfileUrl", config().get(DOCKERFILE_URL))
                .configure("openPorts", config().get(OPEN_PORTS))
                .configure("directPorts", config().get(DIRECT_PORTS)));
    }

}
