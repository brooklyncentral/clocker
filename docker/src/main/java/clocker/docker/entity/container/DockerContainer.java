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
package clocker.docker.entity.container;

import java.util.List;
import java.util.Map;
import java.util.Set;

import clocker.docker.entity.DockerHost;
import clocker.docker.entity.DockerInfrastructure;
import clocker.docker.entity.util.DockerAttributes;
import clocker.docker.location.DockerContainerLocation;

import com.google.common.reflect.TypeToken;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.objs.HasShortName;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.annotation.Effector;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.effector.MethodEffector;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.location.dynamic.LocationOwner;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.stock.BasicStartable;
import org.apache.brooklyn.location.jclouds.JcloudsSshMachineLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.net.HasNetworkAddresses;

/**
 * A Docker container.
 * <p>
 * This entity controls the {@link DockerContainerLocation} location, and creates
 * and the {@link JcloudsSshMachineLocation} that entities communicate with when
 * deployed to the {@link DockerInfrastructure}.
 */
@ImplementedBy(DockerContainerImpl.class)
public interface DockerContainer extends BasicStartable, HasNetworkAddresses, HasShortName, LocationOwner<DockerContainerLocation, DockerContainer> {

    @SetFromFlag("dockerHost")
    AttributeSensorAndConfigKey<Entity, Entity> DOCKER_HOST = ConfigKeys.newSensorAndConfigKey(Entity.class, "docker.host", "The parent Docker host");

    @SetFromFlag("infrastructure")
    AttributeSensorAndConfigKey<Entity, Entity> DOCKER_INFRASTRUCTURE = DockerHost.DOCKER_INFRASTRUCTURE;

    @SetFromFlag("containerName")
    AttributeSensorAndConfigKey<String, String> DOCKER_CONTAINER_NAME = DockerAttributes.DOCKER_CONTAINER_NAME;

    @SetFromFlag("privileged")
    ConfigKey<Boolean> PRIVILEGED = DockerAttributes.PRIVILEGED;

    @SetFromFlag("interactive")
    ConfigKey<Boolean> INTERACTIVE = DockerAttributes.INTERACTIVE;

    @SetFromFlag("managed")
    ConfigKey<Boolean> MANAGED = DockerAttributes.MANAGED;

    @SetFromFlag("password")
    ConfigKey<String> DOCKER_LOGIN_PASSWORD = DockerAttributes.DOCKER_LOGIN_PASSWORD;

    @SetFromFlag("imageId")
    AttributeSensorAndConfigKey<String, String> DOCKER_IMAGE_ID = DockerAttributes.DOCKER_IMAGE_ID;

    @SetFromFlag("imageName")
    AttributeSensorAndConfigKey<String, String> DOCKER_IMAGE_NAME = DockerAttributes.DOCKER_IMAGE_NAME;

    @SetFromFlag("imageTag")
    AttributeSensorAndConfigKey<String, String> DOCKER_IMAGE_TAG = DockerAttributes.DOCKER_IMAGE_TAG;

    @SetFromFlag("entrypoint")
    ConfigKey<List<String>> DOCKER_IMAGE_ENTRYPOINT = DockerAttributes.DOCKER_IMAGE_ENTRYPOINT.getConfigKey();

    @SetFromFlag("commands")
    ConfigKey<List<String>> DOCKER_IMAGE_COMMANDS = DockerAttributes.DOCKER_IMAGE_COMMANDS.getConfigKey();

    @SetFromFlag("hardwareId")
    AttributeSensorAndConfigKey<String, String> DOCKER_HARDWARE_ID = DockerAttributes.DOCKER_HARDWARE_ID;

    @SetFromFlag("useHostDnsName")
    ConfigKey<Boolean> DOCKER_USE_HOST_DNS_NAME = DockerAttributes.DOCKER_USE_HOST_DNS_NAME;

    @SetFromFlag("useSsh")
    ConfigKey<Boolean> DOCKER_USE_SSH = DockerAttributes.DOCKER_USE_SSH;

    @SetFromFlag("cpuShares")
    ConfigKey<Integer> DOCKER_CPU_SHARES = DockerAttributes.DOCKER_CPU_SHARES;

    @SetFromFlag("memory")
    ConfigKey<Integer> DOCKER_MEMORY = DockerAttributes.DOCKER_MEMORY;

    @SetFromFlag("volumes")
    ConfigKey<List<String>> DOCKER_CONTAINER_VOLUME_EXPORT = DockerAttributes.DOCKER_CONTAINER_VOLUME_EXPORT;

    @SetFromFlag("volumesFrom")
    ConfigKey<List<String>> DOCKER_CONTAINER_VOLUMES_FROM = DockerAttributes.DOCKER_CONTAINER_VOLUMES_FROM;

    @SetFromFlag("env")
    AttributeSensorAndConfigKey<Map<String, Object>, Map<String, Object>> DOCKER_CONTAINER_ENVIRONMENT = ConfigKeys.newSensorAndConfigKey(
            new TypeToken<Map<String, Object>>() { },
            "docker.container.environment", "Environment variables for the Docker container");

    @SetFromFlag("entity")
    AttributeSensorAndConfigKey<Entity, Entity> ENTITY = ConfigKeys.newSensorAndConfigKey(Entity.class,
            "docker.container.entity", "The entity running in this Docker container");

    AttributeSensor<String> DOCKER_CONTAINER_ID = DockerAttributes.DOCKER_CONTAINER_ID;

    AttributeSensor<Set<String>> CONTAINER_ADDRESSES = Sensors.newSensor(new TypeToken<Set<String>>() { },
            "docker.container.addresses", "The set of Docker container IP addresses");

    AttributeSensor<Entity> CONTAINER = Sensors.newSensor(Entity.class, "docker.container", "The Docker container entity");

    AttributeSensor<Boolean> CONTAINER_RUNNING = Sensors.newBooleanSensor("docker.container.running", "The Docker container process running status");
    AttributeSensor<Boolean> CONTAINER_PAUSED = Sensors.newBooleanSensor("docker.container.paused", "The Docker container process paused status");

    AttributeSensor<Lifecycle> SERVICE_STATE_ACTUAL = SoftwareProcess.SERVICE_STATE_ACTUAL;

    AttributeSensor<SshMachineLocation> SSH_MACHINE_LOCATION = Sensors.newSensor(SshMachineLocation.class, "docker.container.ssh", "The SSHable machine");

    MethodEffector<Void> SHUT_DOWN = new MethodEffector<Void>(DockerContainer.class, "shutDown");
    MethodEffector<Void> PAUSE = new MethodEffector<Void>(DockerContainer.class, "pause");
    MethodEffector<Void> RESUME = new MethodEffector<Void>(DockerContainer.class, "resume");

    /**
     * Shut-down the Docker container.
     */
    @Effector(description="Shut-down the Docker container")
    void shutDown();

    /**
     * Pause the Docker container.
     */
    @Effector(description="Pause the Docker container")
    void pause();

    /**
     * Resume the Docker container.
     */
    @Effector(description="Resume the Docker container")
    void resume();

    String getDockerContainerName();

    String getContainerId();

    Entity getRunningEntity();

    void setRunningEntity(Entity entity);

    DockerHost getDockerHost();

    SshMachineLocation getMachine();

}
