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
package brooklyn.clocker;

import brooklyn.catalog.Catalog;
import brooklyn.catalog.CatalogConfig;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.container.docker.DockerContainer;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.entity.container.policy.ContainerHeadroomEnricher;
import brooklyn.entity.container.weave.WeaveInfrastructure;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.docker.strategy.BreadthFirstPlacementStrategy;
import brooklyn.location.docker.strategy.DockerAwarePlacementStrategy;
import brooklyn.location.docker.strategy.MaxContainersPlacementStrategy;
import brooklyn.location.docker.strategy.MaxCpuUsagePlacementStrategy;
import brooklyn.location.jclouds.JcloudsLocationConfig;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableList;

/**
 * Brooklyn managed Docker cloud infrastructure.
 */
@Catalog(name="Clocker",
        description="Docker Cloud infrastructure with Weave networking",
        iconUrl="classpath://docker-top-logo.png")
public class DockerCloud extends AbstractApplication {

    @CatalogConfig(label="Docker Version", priority=90)
    public static final ConfigKey<String> DOCKER_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "1.2");

    @CatalogConfig(label="Location Name", priority=80)
    public static final ConfigKey<String> LOCATION_NAME = ConfigKeys.newConfigKeyWithDefault(
            DockerInfrastructure.LOCATION_NAME.getConfigKey(), "my-docker-cloud");

    @CatalogConfig(label="Security Group (Optional)", priority=70)
    public static final ConfigKey<String> SECURITY_GROUP = DockerInfrastructure.SECURITY_GROUP;

    @CatalogConfig(label="Host Cluster Minimum Size", priority=60)
    public static final ConfigKey<Integer> DOCKER_HOST_CLUSTER_MIN_SIZE = ConfigKeys.newConfigKeyWithDefault(DockerInfrastructure.DOCKER_HOST_CLUSTER_MIN_SIZE, 2);

    @CatalogConfig(label="Maximum Containers per Host", priority=50)
    public static final ConfigKey<Integer> DOCKER_CONTAINER_CLUSTER_MAX_SIZE = ConfigKeys.newConfigKeyWithDefault(MaxContainersPlacementStrategy.DOCKER_CONTAINER_CLUSTER_MAX_SIZE, 8);

    @CatalogConfig(label="Maximum CPU usage per Host", priority=50)
    public static final ConfigKey<Double> DOCKER_CONTAINER_CLUSTER_MAX_CPU = ConfigKeys.newConfigKeyWithDefault(MaxCpuUsagePlacementStrategy.DOCKER_CONTAINER_CLUSTER_MAX_CPU, 0.75d);

    @CatalogConfig(label="Containers Headroom", priority=50)
    public static final ConfigKey<Integer> DOCKER_CONTAINER_CLUSTER_HEADROOM = ConfigKeys.newConfigKeyWithDefault(ContainerHeadroomEnricher.CONTAINER_HEADROOM, 4);

    @CatalogConfig(label="Enable Weave SDN", priority=50)
    public static final ConfigKey<Boolean> WEAVE_ENABLED = ConfigKeys.newConfigKeyWithDefault(WeaveInfrastructure.ENABLED, true);

    @Override
    public void initApp() {
        MaxContainersPlacementStrategy maxContainers = new MaxContainersPlacementStrategy();
        maxContainers.injectManagementContext(getManagementContext());
        maxContainers.setConfig(MaxContainersPlacementStrategy.DOCKER_CONTAINER_CLUSTER_MAX_SIZE, getConfig(DOCKER_CONTAINER_CLUSTER_MAX_SIZE));
        
        BreadthFirstPlacementStrategy breadthFirst = new BreadthFirstPlacementStrategy();
        breadthFirst.injectManagementContext(getManagementContext());
        
        MaxCpuUsagePlacementStrategy cpuUsage = new MaxCpuUsagePlacementStrategy();
        cpuUsage.injectManagementContext(getManagementContext());
        cpuUsage.setConfig(MaxCpuUsagePlacementStrategy.DOCKER_CONTAINER_CLUSTER_MAX_CPU, getConfig(DOCKER_CONTAINER_CLUSTER_MAX_CPU));

        // TODO We were hit by https://bugs.debian.org/cgi-bin/bugreport.cgi?bug=712691
        // when trying to open multiple ports on iptables (i.e. "iptables: Resource temporarily unavailable").
        // Possible fixes/workarounds are:
        //  1. Automatically upgrade iptables on the host.
        //  2. Include a retry.
        //  3. Turn off iptables, instead of enabling DockerInfrastructure.OPEN_IPTABLES on the DockerInfrastructure.
        // Currently we've gone for (3).
        
        addChild(EntitySpec.create(DockerInfrastructure.class)
                .configure(DockerInfrastructure.DOCKER_VERSION, getConfig(DOCKER_VERSION))
                .configure(DockerInfrastructure.LOCATION_NAME, getConfig(LOCATION_NAME))
                .configure(DockerInfrastructure.SECURITY_GROUP, getConfig(SECURITY_GROUP))
                .configure(DockerInfrastructure.DOCKER_HOST_CLUSTER_MIN_SIZE, getConfig(DOCKER_HOST_CLUSTER_MIN_SIZE))
                .configure(DockerInfrastructure.REGISTER_DOCKER_HOST_LOCATIONS, false)
                .configure(ContainerHeadroomEnricher.CONTAINER_HEADROOM, getConfig(DOCKER_CONTAINER_CLUSTER_HEADROOM))
                .configure(WeaveInfrastructure.ENABLED, getConfig(WEAVE_ENABLED))
                .configure(DockerInfrastructure.PLACEMENT_STRATEGIES, ImmutableList.<DockerAwarePlacementStrategy>of(
                        maxContainers, 
                        breadthFirst, 
                        cpuUsage))
                .configure(DockerInfrastructure.DOCKER_HOST_SPEC, EntitySpec.create(DockerHost.class)
                        .configure(DockerHost.PROVISIONING_FLAGS, MutableMap.<String,Object>of(
                                JcloudsLocationConfig.MIN_RAM.getName(), 8000,
                                JcloudsLocationConfig.STOP_IPTABLES.getName(), true))
                        .configure(SoftwareProcess.START_TIMEOUT, Duration.minutes(15))
                        .configure(DockerHost.HA_POLICY_ENABLE, true)
                        .configure(DockerHost.DOCKER_HOST_NAME_FORMAT, "docker-%1$s")
                        .configure(DockerHost.DOCKER_CONTAINER_SPEC, EntitySpec.create(DockerContainer.class)
                                .configure(DockerContainer.DOCKER_CONTAINER_NAME_FORMAT, "docker-%2$d")))
                .displayName("Docker Infrastructure"));
    }
}
