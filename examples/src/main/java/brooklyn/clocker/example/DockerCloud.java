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
import brooklyn.location.docker.strategy.CpuUsagePlacementStrategy;
import brooklyn.util.time.Duration;

/**
 * Brooklyn managed Docker cloud infrastructure.
 */
@Catalog(name="Docker Cloud",
        description="Deploys a Docker cloud infrastructure.",
        iconUrl="classpath://docker-top-logo.png")
public class DockerCloud extends AbstractApplication {

    @CatalogConfig(label="Docker Version", priority=0)
    public static final ConfigKey<String> DOCKER_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "1.2");

    @CatalogConfig(label="Location Name", priority=1)
    public static final ConfigKey<String> LOCATION_NAME = ConfigKeys.newConfigKeyWithDefault(
            DockerInfrastructure.LOCATION_NAME.getConfigKey(), "my-docker-cloud");

    @CatalogConfig(label="Security Group (Optional)", priority=1)
    public static final ConfigKey<String> SECURITY_GROUP = ConfigKeys.newConfigKeyWithDefault(
            DockerInfrastructure.SECURITY_GROUP, "");

    @CatalogConfig(label="Host Cluster Minimum Size", priority=1)
    public static final ConfigKey<Integer> DOCKER_HOST_CLUSTER_MIN_SIZE = ConfigKeys.newConfigKeyWithDefault(DockerInfrastructure.DOCKER_HOST_CLUSTER_MIN_SIZE, 2);

    @CatalogConfig(label="Container Cluster Maximum Size", priority=2)
    public static final ConfigKey<Integer> DOCKER_CONTAINER_CLUSTER_MAX_SIZE = ConfigKeys.newConfigKeyWithDefault(DockerHost.DOCKER_CONTAINER_CLUSTER_MAX_SIZE, 4);

    @CatalogConfig(label="Container Cluster Maximum CPU Usage", priority=2)
    public static final ConfigKey<Double> DOCKER_CONTAINER_CLUSTER_MAX_CPU = ConfigKeys.newConfigKeyWithDefault(DockerHost.DOCKER_CONTAINER_CLUSTER_MAX_CPU, 0.5d);

    @CatalogConfig(label="Enable Host HA Policies", priority=2)
    public static final ConfigKey<Boolean> HA_POLICY_ENABLE = DockerHost.HA_POLICY_ENABLE;

    @Override
    public void init() {
        EntitySpec dockerSpec = EntitySpec.create(DockerHost.class)
                .configure(SoftwareProcess.START_TIMEOUT, Duration.minutes(15))
                .configure(DockerHost.HA_POLICY_ENABLE, getConfig(HA_POLICY_ENABLE))
                .configure(DockerHost.DOCKER_CONTAINER_CLUSTER_MAX_SIZE, getConfig(DOCKER_CONTAINER_CLUSTER_MAX_SIZE))
                .configure(DockerHost.DOCKER_CONTAINER_CLUSTER_MAX_CPU, getConfig(DOCKER_CONTAINER_CLUSTER_MAX_CPU));

        addChild(EntitySpec.create(DockerInfrastructure.class)
                .configure(DockerInfrastructure.DOCKER_VERSION, getConfig(DOCKER_VERSION))
                .configure(DockerInfrastructure.SECURITY_GROUP, getConfig(SECURITY_GROUP))
                .configure(DockerInfrastructure.OPEN_IPTABLES, true)
                .configure(DockerInfrastructure.LOCATION_NAME, getConfig(LOCATION_NAME))
                .configure(DockerInfrastructure.DOCKER_HOST_CLUSTER_MIN_SIZE, getConfig(DOCKER_HOST_CLUSTER_MIN_SIZE))
                .configure(DockerInfrastructure.DOCKER_HOST_SPEC, dockerSpec)
                .configure(DockerInfrastructure.PLACEMENT_STRATEGY, new CpuUsagePlacementStrategy()) // TODO make configurable
                .displayName("Docker Infrastructure"));
    }
}
