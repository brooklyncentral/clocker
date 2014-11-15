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

import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.location.docker.DockerHostLocation;

/**
 * Placement strategy that selects the Docker host with the lowest number of containers.
 */
public class LeastContainersPlacementStrategy extends BasicDockerPlacementStrategy {

    @Override
    public int compare(DockerHostLocation l1, DockerHostLocation l2) {
        Integer size1 = l1.getOwner().getAttribute(DockerHost.DOCKER_CONTAINER_CLUSTER).getAttribute(BasicGroup.GROUP_SIZE);
        if (size1 == null) size1 = 0;
        Integer size2 = l2.getOwner().getAttribute(DockerHost.DOCKER_CONTAINER_CLUSTER).getAttribute(BasicGroup.GROUP_SIZE);
        if (size2 == null) size2 = 0;
        return Integer.compare(size1, size2);
    }

}
