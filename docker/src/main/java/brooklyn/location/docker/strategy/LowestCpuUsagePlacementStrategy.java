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
package brooklyn.location.docker.strategy;

import brooklyn.entity.container.docker.DockerHost;
import brooklyn.location.docker.DockerHostLocation;

/**
 * Placement strategy that selects the Docker host with the lowest CPU usage.
 */
public class LowestCpuUsagePlacementStrategy extends BasicDockerPlacementStrategy {

    @Override
    public int compare(DockerHostLocation l1, DockerHostLocation l2) {
        Double cpu1 = l1.getOwner().sensors().get(DockerHost.CPU_USAGE);
        if (cpu1 == null) cpu1 = -1d;
        Double cpu2 = l2.getOwner().sensors().get(DockerHost.CPU_USAGE);
        if (cpu2 == null) cpu2 = -1d;
        return Double.compare(cpu1, cpu2);
    }

}
