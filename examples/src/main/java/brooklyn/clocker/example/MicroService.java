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
@Catalog(name = "Container Service",
        description = "A container micro-service defined by a Docker image",
        iconUrl = "classpath://container.png")
public class MicroService extends AbstractApplication {

    @CatalogConfig(label = "Service Name", priority = 90)
    public static final ConfigKey<String> SERVICE_NAME = ConfigKeys.newStringConfigKey("docker.serviceName", "Container service name", "Container Service");

    @CatalogConfig(label = "Image Name", priority = 80)
    public static final ConfigKey<String> IMAGE_NAME = VanillaDockerApplication.IMAGE_NAME;

    @CatalogConfig(label = "Image Tag", priority = 80)
    public static final ConfigKey<String> IMAGE_TAG = VanillaDockerApplication.IMAGE_TAG;

    @CatalogConfig(label = "Open Ports", priority = 70)
    ConfigKey<String> OPEN_PORTS = ConfigKeys.newStringConfigKey("docker.openPorts", "Comma separated list of ports the application uses");

    @CatalogConfig(label = "Direct Ports", priority = 70)
    ConfigKey<String> DIRECT_PORTS = ConfigKeys.newStringConfigKey("docker.directPorts", "Comma separated list of ports to open directly on the host");

    @Override
    public void initApp() {
        addChild(EntitySpec.create(VanillaDockerApplication.class)
                .configure("imageName", config().get(IMAGE_NAME))
                .configure("imageTag", config().get(IMAGE_TAG))
                .configure("openPorts", config().get(OPEN_PORTS))
                .configure("directPorts", config().get(DIRECT_PORTS))
                .displayName(config().get(SERVICE_NAME)));
    }

}
