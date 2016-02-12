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
package brooklyn.entity.container.docker.registry;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import brooklyn.entity.container.DockerAttributes;
import brooklyn.entity.container.docker.DockerContainer;
import brooklyn.entity.container.docker.application.VanillaDockerApplication;


@Catalog(name = "Docker Registry",
        description = "The Docker Registry",
        iconUrl = DockerRegistryImpl.DOCKER_REGISTRY_LOGO)
@ImplementedBy(DockerRegistryImpl.class)
public interface DockerRegistry extends VanillaDockerApplication {

    ConfigKey<String> IMAGE_NAME = ConfigKeys.newConfigKeyWithDefault(DockerAttributes.DOCKER_IMAGE_NAME.getConfigKey(), "registry");

    @SetFromFlag("imageTag")
    ConfigKey<String> IMAGE_TAG = ConfigKeys.newConfigKeyWithDefault(DockerAttributes.DOCKER_IMAGE_TAG.getConfigKey(), "2");

    @SetFromFlag("registryPort")
    AttributeSensorAndConfigKey<Integer, Integer> DOCKER_REGISTRY_PORT = ConfigKeys.newIntegerSensorAndConfigKey("docker.registry.port", "The docker registry port to expose", 50000);

    AttributeSensorAndConfigKey<Entity, Entity> DOCKER_HOST = DockerContainer.DOCKER_HOST;

    ConfigKey<Map<String, Object>> DOCKER_CONTAINER_ENVIRONMENT = ConfigKeys.newConfigKeyWithDefault(DockerContainer.DOCKER_CONTAINER_ENVIRONMENT.getConfigKey(),
       ImmutableMap.<String, Object>builder()
               .put("REGISTRY_HTTP_TLS_CERTIFICATE", "/certs/repo-cert.pem")
               .put("REGISTRY_HTTP_TLS_KEY", "/certs/repo-key.pem")
               .build());

    AttributeSensor<List<String>> DOCKER_REGISTRY_CATALOG = Sensors.newSensor(new TypeToken<List<String>>() { },
            "docker.registry.catalog", "The docker registry catalog, which lists all of the available repositories");
}
