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
package brooklyn.entity.container.docker.application;

import java.util.List;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.BrooklynConfigKeys;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.VanillaSoftwareProcess;
import brooklyn.entity.container.DockerAttributes;
import brooklyn.entity.container.docker.DockerContainer;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;

@ImplementedBy(VanillaDockerApplicationImpl.class)
public interface VanillaDockerApplication extends VanillaSoftwareProcess {

    @SetFromFlag("startTimeout")
    ConfigKey<Duration> START_TIMEOUT = ConfigKeys.newConfigKeyWithDefault(BrooklynConfigKeys.START_TIMEOUT, Duration.FIVE_MINUTES);

    @SetFromFlag("dockerfileUrl")
    ConfigKey<String> DOCKERFILE_URL = DockerAttributes.DOCKERFILE_URL;

    @SetFromFlag("imageName")
    ConfigKey<String> IMAGE_NAME = DockerAttributes.DOCKER_IMAGE_NAME.getConfigKey();

    @SetFromFlag("imageTag")
    ConfigKey<String> IMAGE_TAG = DockerAttributes.DOCKER_IMAGE_TAG.getConfigKey();

    @SetFromFlag("openPorts")
    ConfigKey<List<Integer>> CONTAINER_PORT_LIST = ConfigKeys.newConfigKey(new TypeToken<List<Integer>>() {},
            "docker.container.openPorts", "List of ports to open on the container for forwarding", ImmutableList.<Integer>of());

    DockerContainer getDockerContainer();
    DockerHost getDockerHost();
    String getDockerfile();
    List<Integer> getContainerPorts();
}
