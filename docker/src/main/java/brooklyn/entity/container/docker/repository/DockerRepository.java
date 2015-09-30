package brooklyn.entity.container.docker.repository;

import brooklyn.entity.container.DockerAttributes;
import brooklyn.entity.container.docker.DockerContainer;
import brooklyn.entity.container.docker.application.VanillaDockerApplication;
import com.google.common.collect.ImmutableMap;
import jdk.nashorn.internal.ir.annotations.Immutable;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import java.util.Map;

@ImplementedBy(DockerRepositoryImpl.class)
public interface DockerRepository extends VanillaDockerApplication {

    @SetFromFlag("imageName")
    ConfigKey<String> IMAGE_NAME = ConfigKeys.newConfigKeyWithDefault(DockerAttributes.DOCKER_IMAGE_NAME.getConfigKey(), "registry");

    @SetFromFlag("imageTag")
    ConfigKey<String> IMAGE_TAG = ConfigKeys.newConfigKeyWithDefault(DockerAttributes.DOCKER_IMAGE_TAG.getConfigKey(), "latest");

    @SetFromFlag("dockerRegistryPort")
    ConfigKey<Integer> DOCKER_REGISTRY_PORT = ConfigKeys.newIntegerConfigKey(
            "docker.registry.port", "The docker registry port to expose", 5000);

    @SetFromFlag("dockerHost")
    AttributeSensorAndConfigKey<Entity, Entity> DOCKER_HOST = DockerContainer.DOCKER_HOST;

    @SetFromFlag("env")
    ConfigKey<Map<String, String>> DOCKER_CONTAINER_ENVIRONMENT = ConfigKeys.newConfigKeyWithDefault(DockerContainer.DOCKER_CONTAINER_ENVIRONMENT.getConfigKey(),
       ImmutableMap.<String, String>of(
                            "REGISTRY_HTTP_TLS_CERTIFICATE", "/certs/repo-cert.pem",
                            "REGISTRY_HTTP_TLS_KEY", "/certs/repo-key.pem"
            ));
}
