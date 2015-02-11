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
package brooklyn.entity.container.docker;

import java.util.List;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.container.DockerAttributes;
import brooklyn.entity.container.DockerUtils;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.machine.MachineEntity;
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
import brooklyn.location.docker.strategy.affinity.AffinityRules;
import brooklyn.location.dynamic.LocationOwner;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsLocationConfig;
import brooklyn.networking.subnet.SubnetTier;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Duration;

import com.google.common.base.Optional;
import com.google.common.reflect.TypeToken;

/**
 * A single machine running Docker.
 * <p>
 * This entity controls the {@link DockerHostLocation} location, and creates
 * and wraps a {@link JcloudsLocation} representing the API for the Docker
 * service on this machine.
 */
@ImplementedBy(DockerHostImpl.class)
public interface DockerHost extends MachineEntity, Resizable, HasShortName, LocationOwner<DockerHostLocation, DockerHost> {

    @SetFromFlag("version")
    ConfigKey<String> DOCKER_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "1.4.1");

    @SetFromFlag("startTimeout")
    ConfigKey<Duration> START_TIMEOUT = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.START_TIMEOUT, Duration.FIVE_MINUTES);

    AttributeSensorAndConfigKey<String, String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "https://get.docker.com/");

    @SetFromFlag("dockerPort")
    PortAttributeSensorAndConfigKey DOCKER_PORT = ConfigKeys.newPortSensorAndConfigKey("docker.port",
            "Docker port", PortRanges.fromInteger(2375));

    @SetFromFlag("dockerSslPort")
    PortAttributeSensorAndConfigKey DOCKER_SSL_PORT = ConfigKeys.newPortSensorAndConfigKey("docker.ssl.port",
            "Docker port", PortRanges.fromInteger(2376));

    @SetFromFlag("openIptables")
    ConfigKey<Boolean> OPEN_IPTABLES = ConfigKeys.newConfigKeyWithPrefix("docker.host.", JcloudsLocationConfig.OPEN_IPTABLES);

    @SetFromFlag("containerSpec")
    AttributeSensorAndConfigKey<EntitySpec, EntitySpec> DOCKER_CONTAINER_SPEC = ConfigKeys.newSensorAndConfigKey(
            EntitySpec.class, "docker.container.spec", "Specification to use when creating child Docker container",
            EntitySpec.create(DockerContainer.class));

    @SetFromFlag("infrastructure")
    AttributeSensorAndConfigKey<Entity, Entity> DOCKER_INFRASTRUCTURE = DockerAttributes.DOCKER_INFRASTRUCTURE;

    ConfigKey<String> DOCKER_HOST_NAME_FORMAT = ConfigKeys.newStringConfigKey("docker.host.nameFormat",
            "Format for generating Docker host names", DockerUtils.DEFAULT_DOCKER_HOST_NAME_FORMAT);

    @SetFromFlag("repository")
    AttributeSensorAndConfigKey<String, String>  DOCKER_REPOSITORY = ConfigKeys.newStringSensorAndConfigKey("docker.repository",
            "The name of the Docker repository for images");

    ConfigKey<? extends String> EPEL_RELEASE = ConfigKeys.newStringConfigKey("docker.host.epel.release",
            "EPEL release for yum based OS", "6-8");

    AttributeSensorAndConfigKey<String, String> DOCKER_IMAGE_ID = DockerAttributes.DOCKER_IMAGE_ID;

    AttributeSensor<String> DOCKER_IMAGE_NAME = DockerAttributes.DOCKER_IMAGE_NAME;

    AttributeSensorAndConfigKey<String, String> DOCKER_HARDWARE_ID = DockerAttributes.DOCKER_HARDWARE_ID;

    @SetFromFlag("volumeMappings")
    AttributeSensorAndConfigKey<Map<String, String>, Map<String, String>> DOCKER_HOST_VOLUME_MAPPING = DockerAttributes.DOCKER_HOST_VOLUME_MAPPING;

    @SetFromFlag("affinityRules")
    ConfigKey<List<String>> DOCKER_HOST_AFFINITY_RULES = AffinityRules.AFFINITY_RULES;

    @SetFromFlag("password")
    ConfigKey<String> DOCKER_PASSWORD = DockerAttributes.DOCKER_PASSWORD;

    AttributeSensor<String> DOCKER_HOST_NAME = Sensors.newStringSensor("docker.host.name", "The name of the Docker host");

    @SetFromFlag("provisioningFlags")
    ConfigKey<Map<String,Object>> PROVISIONING_FLAGS = ConfigKeys.newConfigKey(new TypeToken<Map<String,Object>>() { },
            "docker.host.flags", "Provisioning flags for the Docker hosts", MutableMap.<String,Object>of());

    @SetFromFlag("scanInterval")
    ConfigKey<Duration> SCAN_INTERVAL = ConfigKeys.newConfigKey(Duration.class,
            "docker.host.scanInterval", "Interval between scans of Docker containers", Duration.TEN_SECONDS);
    AttributeSensor<Void> SCAN = Sensors.newSensor(Void.class, "docker.host.scan", "Notification of host scan");

    AttributeSensor<DynamicCluster> DOCKER_CONTAINER_CLUSTER = Sensors.newSensor(DynamicCluster.class,
            "docker.container.cluster", "The cluster of Docker containers");
    AttributeSensor<JcloudsLocation> JCLOUDS_DOCKER_LOCATION = Sensors.newSensor(JcloudsLocation.class,
            "docker.jclouds.location", "The location used for provisioning Docker containers");
    AttributeSensor<SubnetTier> DOCKER_HOST_SUBNET_TIER = Sensors.newSensor(SubnetTier.class,
            "docker.subnetTier", "The SubnetTier for Docker port mapping");

    String getRepository();

    String getPassword();

    Integer getDockerPort();

    JcloudsLocation getJcloudsLocation();

    SubnetTier getSubnetTier();

    String getDockerHostName();

    DynamicCluster getDockerContainerCluster();

    List<Entity> getDockerContainerList();

    DockerInfrastructure getInfrastructure();


    /**
     * As {@link #getImageNamed(String, String)} and looking for the latest image.
     */
    Optional<String> getImageNamed(String name);

    /**
     * @return an Optional containing the ID of the named and tagged image.
     */
    Optional<String> getImageNamed(String name, String tag);

    MethodEffector<String> CREATE_SSHABLE_IMAGE = new MethodEffector<String>(DockerHost.class, "createSshableImage");
    MethodEffector<String> RUN_DOCKER_COMMAND = new MethodEffector<String>(DockerHost.class, "runDockerCommand");
    MethodEffector<String> RUN_DOCKER_COMMAND_TIMEOUT = new MethodEffector<String>(DockerHost.class, "runDockerCommandTimeout");
    MethodEffector<String> DEPLOY_ARCHIVE = new MethodEffector<String>(DockerHost.class, "deployArchive");

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
            @EffectorParam(name="name", description="Repository name") String name);

    /**
     * Create an SSHable image based on the image with the given name.
     *
     * @param baseImage The parent image to base the new image on, e.g. "tomcat" or "redis"
     * @param tag The tag of the parent image, e.g. "latest"
     * @return the new image's ID
     */
    @Effector(description="Create an SSHable image based on the named image and return its ID")
    String layerSshableImageOn(
            @EffectorParam(name="baseImage", description="The image's name") String baseImage,
            @EffectorParam(name="tag", description="The image's tag, e.g. latest") String tag);

    /**
     * Execute a Docker command and return the output.
     *
     * @param command Docker command
     */
    @Effector(description="Execute a Docker command and return the output")
    String runDockerCommand(
            @EffectorParam(name="command", description="Docker command") String command);

    /**
     * Execute a Docker command and return the output, or throw an exception after a timeout.
     *
     * @param command Docker command
     * @param timeout Timeout
     */
    @Effector(description="Execute a Docker command and return the output")
    String runDockerCommandTimeout(
            @EffectorParam(name="command", description="Docker command") String command,
            @EffectorParam(name = "timeout", description = "Timeout") Duration timeout);

    /**
     * Upload an archive file to the host and expand it, for export to a container.
     *
     * @param url Archive source URL
     */
    @Effector(description="Upload an archive file to the host and expand it, for export to a container")
    String deployArchive(
            @EffectorParam(name="url", description="Archive source URL") String url);

}
