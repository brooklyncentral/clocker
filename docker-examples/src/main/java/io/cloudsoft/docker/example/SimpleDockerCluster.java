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
import brooklyn.entity.basic.Entities;
import brooklyn.entity.container.docker.DockerAttributes;
import brooklyn.entity.container.docker.application.VanillaDockerApplication;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.docker.strategy.DockerNodePlacementStrategy;

/**
 * Brooklyn managed {@link VanillaDockerApplication} cluster
 */
@Catalog(name="Docker Cluster",
        description="Simple Docker application cluster.",
        iconUrl="classpath://glossy-3d-blue-web-icon.png")
public class SimpleDockerCluster extends AbstractApplication {

    @CatalogConfig(label="Cluster Size", priority=0)
    public static final ConfigKey<Integer> INITIAL_SIZE = ConfigKeys.newConfigKeyWithDefault(DynamicCluster.INITIAL_SIZE, 6);

    @CatalogConfig(label="Cluster Size", priority=0)
    ConfigKey<String> DOCKERFILE_URL = DockerAttributes.DOCKERFILE_URL;

    @Override
    public void init() {
        addChild(EntitySpec.create(DynamicCluster.class)
                .displayName("Docker Application Cluster")
                .configure(DynamicCluster.INITIAL_SIZE, getConfig(INITIAL_SIZE))
                .configure(DynamicCluster.ENABLE_AVAILABILITY_ZONES, true)
                .configure(DynamicCluster.ZONE_PLACEMENT_STRATEGY, new DockerNodePlacementStrategy())
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(VanillaDockerApplication.class)
                        .configure(VanillaDockerApplication.DOCKERFILE_URL, Entities.getRequiredUrlConfig(this, DOCKERFILE_URL))));
    }

}
