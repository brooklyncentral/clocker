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
package clocker.docker.entity;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import clocker.docker.entity.container.DockerContainer;
import clocker.docker.entity.util.DockerAttributes;
import clocker.docker.entity.util.DockerUtils;
import clocker.docker.location.DockerHostLocation;
import clocker.docker.location.strategy.affinity.AffinityRules;

import com.google.common.base.Optional;
import com.google.common.reflect.TypeToken;

import org.jclouds.net.domain.IpPermission;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.objs.HasShortName;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.annotation.Effector;
import org.apache.brooklyn.core.annotation.EffectorParam;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.effector.MethodEffector;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.core.location.dynamic.LocationOwner;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.machine.MachineEntity;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.time.Duration;

import brooklyn.networking.subnet.SubnetTier;

/**
 * A single machine running Docker.
 * <p>
 * This entity controls the {@link DockerHostLocation} location, and creates
 * and wraps a {@link JcloudsLocation} representing the API for the Docker
 * service on this machine.
 */
@ImplementedBy(DockerHostImpl.class)
public interface DockerHost extends MachineEntity, HasShortName, LocationOwner<DockerHostLocation, DockerHost> {

    @SetFromFlag("dockerVersion")
    ConfigKey<String> DOCKER_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "1.10.3");

    @SetFromFlag("archiveNameFormat")
    ConfigKey<String> ARCHIVE_DIRECTORY_NAME_FORMAT = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.ARCHIVE_DIRECTORY_NAME_FORMAT, "docker-%s");

    @SetFromFlag("startTimeout")
    ConfigKey<Duration> START_TIMEOUT = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.START_TIMEOUT, Duration.FIVE_MINUTES);

    @SetFromFlag("downloadUrl")
    AttributeSensorAndConfigKey<String, String> DOWNLOAD_URL = ConfigKeys.newSensorAndConfigKeyWithDefault(SoftwareProcess.DOWNLOAD_URL,
            "https://get.docker.com/");

    @SetFromFlag("dockerPort")
    PortAttributeSensorAndConfigKey DOCKER_PORT = ConfigKeys.newPortSensorAndConfigKey("docker.port",
            "Docker port", PortRanges.fromInteger(2375));

    @SetFromFlag("dockerSslPort")
    PortAttributeSensorAndConfigKey DOCKER_SSL_PORT = ConfigKeys.newPortSensorAndConfigKey("docker.ssl.port",
            "Docker port", PortRanges.fromInteger(2376));

    @SetFromFlag("openIptables")
    ConfigKey<Boolean> OPEN_IPTABLES = ConfigKeys.newConfigKeyWithPrefix("docker.host.", SoftwareProcess.OPEN_IPTABLES);

    @SetFromFlag("useSsh")
    ConfigKey<Boolean> DOCKER_USE_SSH = DockerAttributes.DOCKER_USE_SSH;

    @SetFromFlag("containerSpec")
    AttributeSensorAndConfigKey<EntitySpec, EntitySpec> DOCKER_CONTAINER_SPEC = ConfigKeys.newSensorAndConfigKey(
            EntitySpec.class, "docker.container.spec", "Specification to use when creating child Docker container",
            EntitySpec.create(DockerContainer.class));

    @SetFromFlag("infrastructure")
    AttributeSensorAndConfigKey<Entity, Entity> DOCKER_INFRASTRUCTURE = DockerAttributes.DOCKER_INFRASTRUCTURE;

    ConfigKey<String> DOCKER_HOST_NAME_FORMAT = ConfigKeys.newStringConfigKey("docker.host.nameFormat",
            "Format for generating Docker host names", DockerUtils.DEFAULT_DOCKER_HOST_NAME_FORMAT);

    ConfigKey<String> EPEL_RELEASE = ConfigKeys.newStringConfigKey("docker.host.epel.release",
            "EPEL release for yum based OS", "6-8");

    ConfigKey<String> DOCKER_STORAGE_DRIVER = ConfigKeys.newStringConfigKey("docker.host.driver.storage",
            "The Docker storage driver type ('devicemapper', 'btrfs', 'aufs' or 'overlay', null uses Docker default)");

    AttributeSensorAndConfigKey<String, String> DOCKER_IMAGE_ID = DockerAttributes.DOCKER_IMAGE_ID;

    AttributeSensor<String> DOCKER_IMAGE_NAME = DockerAttributes.DOCKER_IMAGE_NAME;

    AttributeSensorAndConfigKey<String, String> DOCKER_HARDWARE_ID = DockerAttributes.DOCKER_HARDWARE_ID;

    @SetFromFlag("volumeMappings")
    AttributeSensorAndConfigKey<Map<String, String>, Map<String, String>> DOCKER_HOST_VOLUME_MAPPING = DockerAttributes.DOCKER_HOST_VOLUME_MAPPING;

    @SetFromFlag("password")
    ConfigKey<String> DOCKER_LOGIN_PASSWORD = DockerAttributes.DOCKER_LOGIN_PASSWORD;

    @SetFromFlag("affinityRules")
    ConfigKey<List<String>> DOCKER_HOST_AFFINITY_RULES = AffinityRules.AFFINITY_RULES;

    AttributeSensor<String> DOCKER_HOST_NAME = Sensors.newStringSensor("docker.host.name", "The name of the Docker host");

    @SetFromFlag("provisioningFlags")
    ConfigKey<Map<String,Object>> PROVISIONING_FLAGS = ConfigKeys.newConfigKey(new TypeToken<Map<String,Object>>() { },
            "docker.host.flags", "Provisioning flags for the Docker hosts", MutableMap.<String,Object>of());

    @SetFromFlag("scanInterval")
    ConfigKey<Duration> SCAN_INTERVAL = ConfigKeys.newConfigKey(Duration.class,
            "docker.host.scanInterval", "Interval between scans of Docker containers", Duration.THIRTY_SECONDS);
    AttributeSensor<Void> SCAN = Sensors.newSensor(Void.class, "docker.host.scan", "Notification of host scan");

    AttributeSensor<Group> DOCKER_CONTAINER_CLUSTER = Sensors.newSensor(Group.class,
            "docker.container.cluster", "The cluster of Docker containers");
    AttributeSensor<JcloudsLocation> JCLOUDS_DOCKER_LOCATION = Sensors.newSensor(JcloudsLocation.class,
            "docker.jclouds.location", "The location used for provisioning Docker containers");
    AttributeSensor<SubnetTier> DOCKER_HOST_SUBNET_TIER = Sensors.newSensor(SubnetTier.class,
            "docker.subnetTier", "The SubnetTier for Docker port mapping");

    // TODO add notNull constraint
    @SetFromFlag("addLocalhostPermission")
    ConfigKey<Boolean> ADD_LOCALHOST_PERMISSION = ConfigKeys.newBooleanConfigKey(
            "docker.addLocalhostPermission",
            "When true, will add the localhost IP address to the docker host as an acceptable ingress address. This is useful when running Clocker in the same network as the hosts will be run in (e.g. same AWS availibility zone).",
            false);

    String getLoginPassword();

    Integer getDockerPort();

    JcloudsLocation getJcloudsLocation();

    SubnetTier getSubnetTier();

    String getDockerHostName();

    Group getDockerContainerCluster();

    List<Entity> getDockerContainerList();

    Integer getCurrentSize();

    DockerInfrastructure getInfrastructure();

    // TODO move these to MachineEntity

    /**
     * Runs a Unix command on the {@link SshMachineLocation machine} and returns the exit status.
     *
     * @see MachineEntity#execCommandTimeout(String, Duration)
     */
    int execCommandStatusTimeout(String command, Duration timeout);

    /**
     * @see #execCommandStatusTimeout(String, Duration)
     */
    int execCommandStatus(String command);

    /**
     * As {@link #getImageNamed(String, String)} and looking for the latest image.
     */
    Optional<String> getImageNamed(String name);

    /**
     * @return an Optional containing the ID of the named and tagged image.
     */
    Optional<String> getImageNamed(String name, String tag);

    void configureSecurityGroups();
    void addIpPermissions(Collection<IpPermission> permissions);
    void removeIpPermissions(Collection<IpPermission> permissions);

    MethodEffector<String> BUILD_IMAGE = new MethodEffector<String>(DockerHost.class, "buildImage");
    MethodEffector<String> RUN_DOCKER_COMMAND = new MethodEffector<String>(DockerHost.class, "runDockerCommand");
    MethodEffector<String> RUN_DOCKER_COMMAND_TIMEOUT = new MethodEffector<String>(DockerHost.class, "runDockerCommandTimeout");
    MethodEffector<String> DEPLOY_ARCHIVE = new MethodEffector<String>(DockerHost.class, "deployArchive");

    /**
     * Create an image from a Dockerfile and optional entrypoint script and return the image ID.
     *
     * @param dockerfile URL of Dockerfile template, or an archive including Dockerfile and all required context
     * @param entrypoint URL of entrypoint script for Dockerfile, may be null
     * @param contextArchive URL of context archive for Dockerfile, may be null
     * @param name Registry name
     * @param useSsh Add SSHable layer after building
     * @param substitutions Extra template substitutions for the Dockerfile
     * @see DockerHostDriver#buildImage(String, Optional, String, boolean)
     */
    @Effector(description="Create an image from a Dockerfile and entrypoint script and return the image ID")
    String buildImage(
            @EffectorParam(name="dockerfile", description="URL of Dockerfile template") String dockerfile,
            @EffectorParam(name="entrypoint", description="URL of entrypoint script") String entrypoint,
            @EffectorParam(name="contextArchive", description="URL of context archive") String contextArchive,
            @EffectorParam(name="name", description="Registry name") String name,
            @EffectorParam(name="useSsh", description="Add an SSHable layer after building") boolean useSsh,
            @EffectorParam(name="substitutions", description="Extra template substitutions for the Dockerfile") Map<String, Object> substitutions);


    /**
     * Create an SSHable image based on the fullyQualifiedName provided
     *
     * @param fullyQualifiedName The fully qualified name to base the new image on. E.g. quay.io/graemem/myrepo/redis:2
     * @return the new image's ID
     */
    @Effector(description="Create an SSHable image based on the fullyQualifiedName provided")
    String layerSshableImageOnFullyQualified(
            @EffectorParam(name="fullyQualifiedName", description="The fully qualified name to layer on") String fullyQualifiedName);

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
