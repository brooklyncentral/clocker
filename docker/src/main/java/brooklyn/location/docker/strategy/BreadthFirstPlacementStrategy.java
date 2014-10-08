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
package brooklyn.location.docker.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.location.docker.DockerHostLocation;
import brooklyn.util.flags.SetFromFlag;

/**
 * Placement strategy that adds containers to the smallest Docker host.
 */
public class BreadthFirstPlacementStrategy extends BasicDockerPlacementStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(BreadthFirstPlacementStrategy.class);

    @SetFromFlag("maxContainers")
    public static final ConfigKey<Integer> DOCKER_CONTAINER_CLUSTER_MAX_SIZE = DepthFirstPlacementStrategy.DOCKER_CONTAINER_CLUSTER_MAX_SIZE;

    @Override
    public boolean apply(DockerHostLocation input) {
        Integer maxSize = getConfig(DOCKER_CONTAINER_CLUSTER_MAX_SIZE);
        Integer currentSize = input.getOwner().getCurrentSize();
        return currentSize < maxSize;
    }

    @Override
    public int compare(DockerHostLocation l1, DockerHostLocation l2) {
        Integer size1 = l1.getOwner().getCurrentSize();
        Integer size2 = l2.getOwner().getCurrentSize();
        return Integer.compare(size1, size2);
    }

}
