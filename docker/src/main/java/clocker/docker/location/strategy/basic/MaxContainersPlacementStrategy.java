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
package clocker.docker.location.strategy.basic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clocker.docker.entity.DockerHost;
import clocker.docker.entity.DockerInfrastructure;
import clocker.docker.location.DockerHostLocation;
import clocker.docker.location.strategy.BasicDockerPlacementStrategy;

import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.entity.group.BasicGroup;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

/**
 * Placement strategy that limits Docker hosts to a maximum number of containers.
 * <p>
 * Maximum is configured using {@link #DOCKER_CONTAINER_CLUSTER_MAX_SIZE} with settings on
 * the infrastructure overriding if set, if nothing is configured the default is used.
 */
public class MaxContainersPlacementStrategy extends BasicDockerPlacementStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(MaxContainersPlacementStrategy.class);

    @SetFromFlag("maxContainers")
    public static final ConfigKey<Integer> DOCKER_CONTAINER_CLUSTER_MAX_SIZE = ConfigKeys.newIntegerConfigKey(
            "docker.container.cluster.maxSize",
            "Maximum size of a Docker container cluster");

    public static final Integer DEFAULT_MAX_CONTAINERS = 8;

    @Override
    public boolean apply(DockerHostLocation input) {
        Integer maxSize = config().get(DOCKER_CONTAINER_CLUSTER_MAX_SIZE);
        DockerInfrastructure infrastructure = config().get(DOCKER_INFRASTRUCTURE);
        if (infrastructure != null) {
            Integer infrastructureMax = infrastructure.config().get(DOCKER_CONTAINER_CLUSTER_MAX_SIZE);
            if (infrastructureMax != null) maxSize = infrastructureMax;
        }
        if (maxSize == null) maxSize = DEFAULT_MAX_CONTAINERS;

        Integer currentSize = input.getOwner().sensors().get(DockerHost.DOCKER_CONTAINER_CLUSTER).sensors().get(BasicGroup.GROUP_SIZE);
        boolean accept = currentSize < maxSize;
        LOG.debug("Location {} size is {}/{}: {}", new Object[] { input, currentSize, maxSize, accept ? "accepted" : "rejected" });
        return accept;
    }

}
