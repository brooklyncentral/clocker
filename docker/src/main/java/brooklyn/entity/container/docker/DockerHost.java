/*
 * Copyright 2014 by Cloudsoft Corporation Limited
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
package brooklyn.entity.container.docker;

import java.util.List;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.HasShortName;
import brooklyn.entity.trait.Resizable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.location.docker.DockerHostLocation;
import brooklyn.location.dynamic.LocationOwner;
import brooklyn.util.flags.SetFromFlag;

/**
 * @author Andrea Turli
 */
@Catalog(name = "Docker Node", description = "Docker is an open-source engine to easily create lightweight, portable, " +
        "self-sufficient containers from any application.",
        iconUrl = "classpath:///docker-top-logo.png")
@ImplementedBy(DockerHostImpl.class)
public interface DockerHost extends SoftwareProcess, Resizable, HasShortName, LocationOwner<DockerHostLocation, DockerHost> {

    String DEFAULT_DOCKER_HOST_NAME_FORMAT = "docker-host-brooklyn-%1$s";

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "0.8");
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "https://get.docker.io/builds/Linux/x86_64/docker-latest");

    @SetFromFlag("maxSize")
    ConfigKey<Integer> DOCKER_CONTAINER_CLUSTER_MAX_SIZE = DockerInfrastructure.DOCKER_CONTAINER_CLUSTER_MAX_SIZE;

    @SetFromFlag("highAvailabilty")
    ConfigKey<Boolean> HA_POLICY_ENABLE = ConfigKeys.newBooleanConfigKey("docker.policy.ha.enable",
            "Enable high-availability and resilience/restart policies", false);

    @SetFromFlag("dockerPort")
    PortAttributeSensorAndConfigKey DOCKER_PORT = new PortAttributeSensorAndConfigKey("docker.port",
            "Docker port", "4243+");

    @SetFromFlag("containerSpec")
    BasicAttributeSensorAndConfigKey<EntitySpec> DOCKER_CONTAINER_SPEC = new
            BasicAttributeSensorAndConfigKey<EntitySpec>(
            EntitySpec.class, "docker.container.spec", "Specification to use when creating child Docker container",
            EntitySpec.create(DockerContainer.class));

    @SetFromFlag("infrastructure")
    ConfigKey<DockerInfrastructure> DOCKER_INFRASTRUCTURE = ConfigKeys.newConfigKey(DockerInfrastructure.class,
            "docker.infrastructure", "The parent Docker infrastructure");

    ConfigKey<String> HOST_NAME_FORMAT = ConfigKeys.newStringConfigKey("docker.host.nameFormat",
            "Format for generating Docker host names", DEFAULT_DOCKER_HOST_NAME_FORMAT);

    AttributeSensor<String> HOST_NAME = Sensors.newStringSensor("docker.host.name", "The name of the Docker host");

    String getDockerHostName();

    DynamicCluster getDockerContainerCluster();

    List<Entity> getDockerContainerList();

    DockerInfrastructure getInfrastructure();

}
