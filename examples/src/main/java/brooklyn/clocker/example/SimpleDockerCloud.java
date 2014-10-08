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
package brooklyn.clocker.example;

import brooklyn.catalog.Catalog;
import brooklyn.catalog.CatalogConfig;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.docker.strategy.BreadthFirstPlacementStrategy;
import brooklyn.location.docker.strategy.DockerAwarePlacementStrategy;
import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableList;

/**
 * Brooklyn managed Docker cloud infrastructure.
 */
@Catalog(name="Docker Cloud",
        description="Simple Clocker infrastructure for Docker cloud",
        iconUrl="classpath://docker-top-logo.png")
public class SimpleDockerCloud extends AbstractApplication {

    @CatalogConfig(label="Docker Version", priority=90)
    public static final ConfigKey<String> DOCKER_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "1.2");

    @CatalogConfig(label="Location Name", priority=80)
    public static final ConfigKey<String> LOCATION_NAME = ConfigKeys.newConfigKeyWithDefault(
            DockerInfrastructure.LOCATION_NAME.getConfigKey(), "my-docker-cloud");

    @CatalogConfig(label="Security Group (Optional)", priority=70)
    public static final ConfigKey<String> SECURITY_GROUP = ConfigKeys.newConfigKeyWithDefault(
            DockerInfrastructure.SECURITY_GROUP, "");

    @CatalogConfig(label="Host Cluster Minimum Size", priority=60)
    public static final ConfigKey<Integer> DOCKER_HOST_CLUSTER_MIN_SIZE = ConfigKeys.newConfigKeyWithDefault(DockerInfrastructure.DOCKER_HOST_CLUSTER_MIN_SIZE, 2);

    @CatalogConfig(label="Maximum Containers per Host", priority=50)
    public static final ConfigKey<Integer> DOCKER_CONTAINER_CLUSTER_MAX_SIZE = ConfigKeys.newConfigKeyWithDefault(DockerHost.DOCKER_CONTAINER_CLUSTER_MAX_SIZE, 4);

    @Override
    public void initApp() {
        EntitySpec dockerSpec = EntitySpec.create(DockerHost.class)
                .configure(SoftwareProcess.START_TIMEOUT, Duration.minutes(15));

        BreadthFirstPlacementStrategy strategy = new BreadthFirstPlacementStrategy();
        strategy.injectManagementContext(getManagementContext());
        strategy.setConfig(BreadthFirstPlacementStrategy.DOCKER_CONTAINER_CLUSTER_MAX_SIZE, getConfig(DOCKER_CONTAINER_CLUSTER_MAX_SIZE));

        addChild(EntitySpec.create(DockerInfrastructure.class)
                .configure(DockerInfrastructure.DOCKER_VERSION, getConfig(DOCKER_VERSION))
                .configure(DockerInfrastructure.SECURITY_GROUP, getConfig(SECURITY_GROUP))
                .configure(DockerInfrastructure.OPEN_IPTABLES, true)
                .configure(DockerInfrastructure.LOCATION_NAME, getConfig(LOCATION_NAME))
                .configure(DockerInfrastructure.DOCKER_HOST_CLUSTER_MIN_SIZE, getConfig(DOCKER_HOST_CLUSTER_MIN_SIZE))
                .configure(DockerInfrastructure.DOCKER_HOST_SPEC, dockerSpec)
                .configure(DockerInfrastructure.PLACEMENT_STRATEGIES, ImmutableList.<DockerAwarePlacementStrategy>of(strategy))
                .displayName("Docker Infrastructure"));
    }
}
