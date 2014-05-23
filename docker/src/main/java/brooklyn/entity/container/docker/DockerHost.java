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

import io.cloudsoft.networking.subnet.PortForwarder;
import io.cloudsoft.networking.subnet.SubnetTier;

import java.util.List;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.HasShortName;
import brooklyn.entity.trait.Resizable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.location.basic.PortRanges;
import brooklyn.location.docker.DockerHostLocation;
import brooklyn.location.dynamic.LocationOwner;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.util.flags.SetFromFlag;

/**
 * A single machine running Docker.
 * <p>
 * This entity controls the {@link DockerHostLocation} location, and creates
 * and wraps a {@link JcloudsLocatiopn} representing the API for the Docker
 * service on this machine.
 */
@ImplementedBy(DockerHostImpl.class)
public interface DockerHost extends SoftwareProcess, Resizable, HasShortName, LocationOwner<DockerHostLocation, DockerHost> {

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
            "Docker port", PortRanges.fromString("4243+"));

    @SetFromFlag("containerSpec")
    AttributeSensorAndConfigKey<EntitySpec, EntitySpec> DOCKER_CONTAINER_SPEC = ConfigKeys.newSensorAndConfigKey(
            EntitySpec.class, "docker.container.spec", "Specification to use when creating child Docker container",
            EntitySpec.create(DockerContainer.class));

    @SetFromFlag("infrastructure")
    ConfigKey<DockerInfrastructure> DOCKER_INFRASTRUCTURE = ConfigKeys.newConfigKey(DockerInfrastructure.class,
            "docker.infrastructure", "The parent Docker infrastructure");

    ConfigKey<String> HOST_NAME_FORMAT = ConfigKeys.newStringConfigKey("docker.host.nameFormat",
            "Format for generating Docker host names", DockerAttributes.DEFAULT_DOCKER_HOST_NAME_FORMAT);

    ConfigKey<? extends String> EPEL_RELEASE = ConfigKeys.newStringConfigKey("docker.host.epel.release",
            "EPEL release for yum based OS", "6-8");

    AttributeSensorAndConfigKey<String, String> DOCKER_IMAGE_ID = DockerAttributes.DOCKER_IMAGE_ID;

    AttributeSensorAndConfigKey<String, String> DOCKER_HARDWARE_ID = DockerAttributes.DOCKER_HARDWARE_ID;

    AttributeSensor<String> HOST_NAME = Sensors.newStringSensor("docker.host.name", "The name of the Docker host");

    Integer getDockerPort();

    JcloudsLocation getJcloudsLocation();

    PortForwarder getPortForwarder();

    SubnetTier getSubnetTier();

    String getDockerHostName();

    DynamicCluster getDockerContainerCluster();

    List<Entity> getDockerContainerList();

    DockerInfrastructure getInfrastructure();

    /**
     * Create an SSHable image and returns the image ID.
     *
     * @param dockerFile URL of Dockerfile to copy
     * @param name Repository name
     * @see DockerHostDriver#buildImage(String, String)
     */
    @Effector(description="Create an SSHable image and returns the image ID")
    String createSshableImage(
            @EffectorParam(name="dockerFile", description="URL of Dockerfile to copy") String dockerFile,
            @EffectorParam(name="folder", description="Repository name") String name);


    /**
     * Execute a Docker command and return the output.
     *
     * @param command Docker command
     */
    @Effector(description="Execute a Docker command and return the output")
    String runDockerCommand(
            @EffectorParam(name="command", description="Docker command") String command);

}
