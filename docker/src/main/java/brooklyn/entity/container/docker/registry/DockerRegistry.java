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
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.container.docker.application.VanillaDockerApplication;


@Catalog(name = "Docker Registry",
        description = "A Docker Registry")
@ImplementedBy(DockerRegistryImpl.class)
public interface DockerRegistry extends VanillaDockerApplication {

    ConfigKey<String> IMAGE_NAME = ConfigKeys.newConfigKeyWithDefault(DockerAttributes.DOCKER_IMAGE_NAME.getConfigKey(), "registry");

    @SetFromFlag("imageTag")
    ConfigKey<String> IMAGE_TAG = ConfigKeys.newConfigKeyWithDefault(DockerAttributes.DOCKER_IMAGE_TAG.getConfigKey(), "2");

    @SetFromFlag("registryPort")
    AttributeSensorAndConfigKey<Integer, Integer> DOCKER_REGISTRY_PORT = DockerHost.DOCKER_REGISTRY_PORT;

    AttributeSensorAndConfigKey<Entity, Entity> DOCKER_HOST = DockerContainer.DOCKER_HOST;

    ConfigKey<Map<String, Object>> DOCKER_CONTAINER_ENVIRONMENT = ConfigKeys.newConfigKeyWithDefault(DockerContainer.DOCKER_CONTAINER_ENVIRONMENT.getConfigKey(),
       ImmutableMap.<String, Object>of(
                            "REGISTRY_HTTP_TLS_CERTIFICATE", "/certs/repo-cert.pem",
                            "REGISTRY_HTTP_TLS_KEY", "/certs/repo-key.pem"
            ));

    AttributeSensor<List<String>> DOCKER_REGISTRY_CATALOG = Sensors.newSensor(new TypeToken<List<String>>(){}, "docker.registry.catalog", "The docker registry catalog, which lists all of the available repositories");
}
