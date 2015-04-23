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
package brooklyn.clocker;

import brooklyn.catalog.Catalog;
import brooklyn.catalog.CatalogConfig;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.entity.proxying.EntitySpec;

/**
 * Brooklyn managed local Docker infrastructure.
 */
@Catalog(name="Local Clocker",
        description="Local Docker infrastructure",
        iconUrl="classpath://docker-logo.png")
public class LocalDocker extends AbstractApplication {

    @CatalogConfig(label="Docker Version", priority=90)
    public static final ConfigKey<String> DOCKER_VERSION = ConfigKeys.newConfigKeyWithDefault(DockerInfrastructure.DOCKER_VERSION, "1.6.0");

    @CatalogConfig(label="Location Name", priority=80)
    public static final ConfigKey<String> LOCATION_NAME = ConfigKeys.newConfigKeyWithDefault(
            DockerInfrastructure.LOCATION_NAME.getConfigKey(), "my-docker-cloud");

    @CatalogConfig(label="Docker Installed", priority=70)
    public static final ConfigKey<Boolean> DOCKER_INSTALLED = ConfigKeys.newBooleanConfigKey("docker.installed", "Docker already pre-installed", false);

    @Override
    public void initApp() {
        addChild(EntitySpec.create(DockerInfrastructure.class)
                .configure(DockerInfrastructure.DOCKER_VERSION, getConfig(DOCKER_VERSION))
                .configure(DockerInfrastructure.LOCATION_NAME, getConfig(LOCATION_NAME))
                .configure(DockerInfrastructure.DOCKER_HOST_CLUSTER_MIN_SIZE, 1)
                .configure(SoftwareProcess.SKIP_INSTALLATION, getConfig(DOCKER_INSTALLED))
                .displayName("Docker Infrastructure"));
    }
}
