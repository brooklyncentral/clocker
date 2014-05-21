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
package io.cloudsoft.docker.example;

import brooklyn.catalog.Catalog;
import brooklyn.catalog.CatalogConfig;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.entity.proxying.EntitySpec;

/**
 * Brooklyn managed basic Docker infrastructure.
 */
@Catalog(name="BasicDockerInfrastructure",
        description="Deploys Simple Docker Infrastructure.",
        iconUrl="classpath://docker-top-logo.png")
public class BasicInfrastructure extends AbstractApplication {

    @CatalogConfig(label="Location Name", priority=1)
    public static final ConfigKey<String> LOCATION_NAME = ConfigKeys.newConfigKeyWithDefault(
            DockerInfrastructure.LOCATION_NAME.getConfigKey(), "docker-infrastructure");

    @CatalogConfig(label="Docker Host Cluster Minimum Size", priority=1)
    public static final ConfigKey<Integer> DOCKER_HOST_CLUSTER_MIN_SIZE = ConfigKeys.newConfigKeyWithDefault(DockerInfrastructure.DOCKER_HOST_CLUSTER_MIN_SIZE, 1);

    @CatalogConfig(label="Enable HA Policies", priority=1)
    public static final ConfigKey<Boolean> HA_POLICY_ENABLE = DockerHost.HA_POLICY_ENABLE;

    @Override
    public void init() {
        EntitySpec dockerSpec = EntitySpec.create(DockerHost.class)
                .configure(DockerHost.HA_POLICY_ENABLE, getConfig(HA_POLICY_ENABLE))
                .configure(DockerHost.DOCKER_CONTAINER_CLUSTER_MAX_SIZE, 4);

        addChild(EntitySpec.create(DockerInfrastructure.class)
                .configure(DockerInfrastructure.SECURITY_GROUP, "universal") // AWS EC2 All TCP and UDP ports from 0.0.0.0/0
                .configure(DockerInfrastructure.OPEN_IPTABLES, true)
                .configure(DockerInfrastructure.LOCATION_NAME, getConfig(LOCATION_NAME))
                .configure(DockerInfrastructure.DOCKER_HOST_CLUSTER_MIN_SIZE, getConfig(DOCKER_HOST_CLUSTER_MIN_SIZE))
                .configure(DockerInfrastructure.DOCKER_HOST_SPEC, dockerSpec)
                .displayName("Docker Infrastructure"));
    }

}
