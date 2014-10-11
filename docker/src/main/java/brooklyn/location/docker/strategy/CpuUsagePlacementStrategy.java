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
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.location.docker.DockerHostLocation;
import brooklyn.util.flags.SetFromFlag;

/**
 * Placement strategy that selects the Docker host with the lowest CPU usage.
 */
public class CpuUsagePlacementStrategy extends BasicDockerPlacementStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(CpuUsagePlacementStrategy.class);

    @SetFromFlag("maxCpuUsage")
    public static final ConfigKey<Double> DOCKER_CONTAINER_CLUSTER_MAX_CPU = ConfigKeys.newDoubleConfigKey(
            "docker.container.cluster.maxCpu",
            "Maximum CPU usage across a Docker container cluster", 0.5d);

    @Override
    public boolean apply(DockerHostLocation input) {
        Double maxCpu = getConfig(DOCKER_CONTAINER_CLUSTER_MAX_CPU);
        Double currentCpu = input.getOwner().getAttribute(DockerHost.CPU_USAGE);
        boolean accept = currentCpu < maxCpu;
        if (LOG.isDebugEnabled()) {
            LOG.debug("Location {} CPU usage is {}: {}", new Object[] { input, currentCpu, accept ? "accepted" : "rejected" });
        }
        return accept;
    }

    @Override
    public int compare(DockerHostLocation l1, DockerHostLocation l2) {
        Double cpu1 = l1.getOwner().getAttribute(DockerHost.CPU_USAGE);
        Double cpu2 = l2.getOwner().getAttribute(DockerHost.CPU_USAGE);
        return Double.compare(cpu1, cpu2);
    }

}
