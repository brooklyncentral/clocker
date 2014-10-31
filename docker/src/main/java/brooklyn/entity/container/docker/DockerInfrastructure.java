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
import brooklyn.entity.basic.BasicStartable;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.container.DockerAttributes;
import brooklyn.entity.container.DockerUtils;
import brooklyn.entity.container.weave.WeaveInfrastructure;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.group.DynamicMultiGroup;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Resizable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.location.docker.DockerLocation;
import brooklyn.location.docker.strategy.DepthFirstPlacementStrategy;
import brooklyn.location.docker.strategy.DockerAwarePlacementStrategy;
import brooklyn.location.docker.strategy.affinity.AffinityRules;
import brooklyn.location.dynamic.LocationOwner;
import brooklyn.location.jclouds.JcloudsLocationConfig;
import brooklyn.util.collections.MutableList;
import brooklyn.util.flags.SetFromFlag;

/**
 * A collection of machines running Docker.
 */
@Catalog(name = "Docker Infrastructure",
        description = "Docker is an open-source engine to easily create lightweight, portable, self-sufficient containers from any application.",
        iconUrl = "classpath:///docker-top-logo.png")
@ImplementedBy(DockerInfrastructureImpl.class)
public interface DockerInfrastructure extends BasicStartable, Resizable, LocationOwner<DockerLocation, DockerInfrastructure> {

    @SetFromFlag("version")
    ConfigKey<String> DOCKER_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "1.3");

    @SetFromFlag("securityGroup")
    ConfigKey<String> SECURITY_GROUP = ConfigKeys.newStringConfigKey(
            "docker.host.securityGroup", "Set a network security group for cloud servers to use; (null to use default configuration)");

    @SetFromFlag("openIptables")
    ConfigKey<Boolean> OPEN_IPTABLES = ConfigKeys.newConfigKeyWithPrefix("docker.host.", JcloudsLocationConfig.OPEN_IPTABLES);

    @SetFromFlag("minHost")
    ConfigKey<Integer> DOCKER_HOST_CLUSTER_MIN_SIZE = ConfigKeys.newConfigKeyWithPrefix("docker.host.", DynamicCluster.INITIAL_SIZE);

    @SetFromFlag("strategies")
    ConfigKey<List<DockerAwarePlacementStrategy>> PLACEMENT_STRATEGIES = ConfigKeys.newConfigKeyWithDefault(DockerAttributes.PLACEMENT_STRATEGIES,
            MutableList.<DockerAwarePlacementStrategy>of(new DepthFirstPlacementStrategy()));

    @SetFromFlag("registerHosts")
    ConfigKey<Boolean> REGISTER_DOCKER_HOST_LOCATIONS = ConfigKeys.newBooleanConfigKey("docker.host.register",
            "Register new Docker Host locations for deployment", Boolean.FALSE);

    @SetFromFlag("removeEmptyHosts")
    ConfigKey<Boolean> REMOVE_EMPTY_DOCKER_HOSTS = ConfigKeys.newBooleanConfigKey("docker.host.removeEmpty",
            "Remove empty Docker Hosts with no containers", Boolean.FALSE);

    @SetFromFlag("enableWeave")
    ConfigKey<Boolean> WEAVE_ENABLED = WeaveInfrastructure.ENABLED;

    @SetFromFlag("hostSpec")
    BasicAttributeSensorAndConfigKey<EntitySpec> DOCKER_HOST_SPEC = new BasicAttributeSensorAndConfigKey<EntitySpec>(
            EntitySpec.class, "docker.host.spec", "Specification to use when creating child Docker Hosts",
            EntitySpec.create(DockerHost.class));

    @SetFromFlag("dockerfileUrl")
    ConfigKey<String> DOCKERFILE_URL = ConfigKeys.newConfigKeyWithDefault(DockerAttributes.DOCKERFILE_URL, DockerUtils.UBUNTU_DOCKERFILE);

    @SetFromFlag("dockerfileName")
    ConfigKey<String> DOCKERFILE_NAME = ConfigKeys.newConfigKeyWithDefault(DockerAttributes.DOCKERFILE_NAME, "ubuntu");

    @SetFromFlag("imageId")
    ConfigKey<String> DOCKER_IMAGE_ID = DockerAttributes.DOCKER_IMAGE_ID.getConfigKey();

    @SetFromFlag("hardwareId")
    ConfigKey<String> DOCKER_HARDWARE_ID = DockerAttributes.DOCKER_HARDWARE_ID.getConfigKey();

    @SetFromFlag("affinityRules")
    ConfigKey<List<String>> DOCKER_HOST_AFFINITY_RULES = AffinityRules.AFFINITY_RULES;

    AttributeSensor<DynamicCluster> DOCKER_HOST_CLUSTER = Sensors.newSensor(DynamicCluster.class, "docker.hosts", "Docker host cluster");
    AttributeSensor<DynamicGroup> DOCKER_CONTAINER_FABRIC = Sensors.newSensor(DynamicGroup.class, "docker.fabric", "Docker container fabric");
    AttributeSensor<DynamicMultiGroup> DOCKER_APPLICATIONS = Sensors.newSensor(DynamicMultiGroup.class, "docker.buckets", "Docker applications");
    AttributeSensor<WeaveInfrastructure> WEAVE_INFRASTRUCTURE = Sensors.newSensor(WeaveInfrastructure.class, "weave.infrastructure", "Weave infrastructure");

    AttributeSensor<Integer> DOCKER_HOST_COUNT = DockerAttributes.DOCKER_HOST_COUNT;
    AttributeSensor<Integer> DOCKER_CONTAINER_COUNT = DockerAttributes.DOCKER_CONTAINER_COUNT;

    List<Entity> getDockerHostList();

    DynamicCluster getDockerHostCluster();

    List<Entity> getDockerContainerList();

    DynamicGroup getContainerFabric();
}
