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
package clocker.docker.entity.container.application;

import java.util.List;
import java.util.Map;

import clocker.docker.entity.DockerHost;
import clocker.docker.entity.container.DockerContainer;
import clocker.docker.entity.util.DockerAttributes;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.time.Duration;

@Catalog(name = "Docker Container",
        description = "A micro-service running in a Docker container.",
        iconUrl = "classpath:///container.png")
@ImplementedBy(VanillaDockerApplicationImpl.class)
public interface VanillaDockerApplication extends VanillaSoftwareProcess {

    @SetFromFlag("startTimeout")
    ConfigKey<Duration> START_TIMEOUT = ConfigKeys.newConfigKeyWithDefault(BrooklynConfigKeys.START_TIMEOUT, Duration.FIVE_MINUTES);

    @SetFromFlag("dockerfileUrl")
    ConfigKey<String> DOCKERFILE_URL = DockerAttributes.DOCKERFILE_URL;

    @SetFromFlag("containerName")
    ConfigKey<String> CONTAINER_NAME = DockerContainer.DOCKER_CONTAINER_NAME.getConfigKey();

    @SetFromFlag("imageName")
    ConfigKey<String> IMAGE_NAME = DockerAttributes.DOCKER_IMAGE_NAME.getConfigKey();

    @SetFromFlag("imageTag")
    ConfigKey<String> IMAGE_TAG = DockerAttributes.DOCKER_IMAGE_TAG.getConfigKey();

    @SetFromFlag("entrypoint")
    ConfigKey<List<String>> IMAGE_ENTRYPOINT = DockerAttributes.DOCKER_IMAGE_ENTRYPOINT.getConfigKey();

    @SetFromFlag("commands")
    ConfigKey<List<String>> IMAGE_COMMANDS = DockerAttributes.DOCKER_IMAGE_COMMANDS.getConfigKey();

    @SetFromFlag("useSsh")
    ConfigKey<Boolean> DOCKER_USE_SSH = ConfigKeys.newConfigKeyWithDefault(DockerAttributes.DOCKER_USE_SSH, Boolean.FALSE);

    @SetFromFlag("openPorts")
    ConfigKey<List<Integer>> DOCKER_OPEN_PORTS = DockerAttributes.DOCKER_OPEN_PORTS;

    @SetFromFlag("directPorts")
    ConfigKey<List<Integer>> DOCKER_DIRECT_PORTS = DockerAttributes.DOCKER_DIRECT_PORTS;

    @SetFromFlag("portBindings")
    ConfigKey<Map<Integer, Integer>> DOCKER_PORT_BINDINGS = DockerAttributes.DOCKER_PORT_BINDINGS;

    @SetFromFlag("env")
    ConfigKey<Map<String, Object>> DOCKER_CONTAINER_ENVIRONMENT = DockerContainer.DOCKER_CONTAINER_ENVIRONMENT.getConfigKey();

    @SetFromFlag("volumes")
    ConfigKey<List<String>> DOCKER_CONTAINER_VOLUME_EXPORT = DockerAttributes.DOCKER_CONTAINER_VOLUME_EXPORT;

    @SetFromFlag("volumesFrom")
    ConfigKey<List<String>> DOCKER_CONTAINER_VOLUMES_FROM = DockerAttributes.DOCKER_CONTAINER_VOLUMES_FROM;

    @SetFromFlag("volumeMappings")
    AttributeSensorAndConfigKey<Map<String, String>, Map<String, String>> DOCKER_HOST_VOLUME_MAPPING = DockerAttributes.DOCKER_HOST_VOLUME_MAPPING;

    @SetFromFlag("links")
    ConfigKey<List<Entity>> DOCKER_LINKS = DockerAttributes.DOCKER_LINKS;

    ConfigKey<Boolean> SKIP_ENTITY_INSTALLATION = ConfigKeys.newConfigKeyWithDefault(BrooklynConfigKeys.SKIP_ENTITY_INSTALLATION, Boolean.TRUE);
    
    ConfigKey<Boolean> SKIP_ON_BOX_BASE_DIR_RESOLUTION = ConfigKeys.newConfigKeyWithDefault(BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION, Boolean.TRUE);

    ConfigKey<String> LAUNCH_COMMAND = ConfigKeys.newConfigKeyWithDefault(VanillaSoftwareProcess.LAUNCH_COMMAND, null);

    DockerContainer getDockerContainer();

    DockerHost getDockerHost();

    String getDockerfile();

    List<Integer> getContainerPorts();

}
