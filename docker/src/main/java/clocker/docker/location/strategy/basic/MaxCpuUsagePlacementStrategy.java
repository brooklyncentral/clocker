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
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

/**
 * Placement strategy that selects only Docker hosts with low CPU usage.
 * <p>
 * Maximum is configured using {@link #DOCKER_CONTAINER_CLUSTER_MAX_CPU} with settings on
 * the infrastructure overriding if set, if nothing is configured the default is used.
 */
public class MaxCpuUsagePlacementStrategy extends BasicDockerPlacementStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(MaxCpuUsagePlacementStrategy.class);

    @SetFromFlag("maxCpuUsage")
    public static final ConfigKey<Double> DOCKER_CONTAINER_CLUSTER_MAX_CPU = ConfigKeys.newDoubleConfigKey(
            "docker.container.cluster.maxCpu",
            "Maximum CPU usage across a Docker container cluster");

    public static final Double DEFAULT_MAX_CPU_USAGE = 0.5d;

    @Override
    public boolean apply(DockerHostLocation input) {
        Double maxCpu = config().get(DOCKER_CONTAINER_CLUSTER_MAX_CPU);
        DockerInfrastructure infrastructure = config().get(DOCKER_INFRASTRUCTURE);
        if (infrastructure != null) {
            Double infrastructureMax = infrastructure.config().get(DOCKER_CONTAINER_CLUSTER_MAX_CPU);
            if (infrastructureMax != null) maxCpu = infrastructureMax;
        }
        if (maxCpu == null) maxCpu = DEFAULT_MAX_CPU_USAGE;

        Boolean serviceUp = input.getOwner().sensors().get(SoftwareProcess.SERVICE_UP);
        Double currentCpu = input.getOwner().sensors().get(DockerHost.CPU_USAGE);
        if (!Boolean.TRUE.equals(serviceUp) || currentCpu == null) return false; // reject

        boolean accept = currentCpu < maxCpu;
        LOG.debug("Location {} CPU usage is {}: {}", new Object[] { input, currentCpu, accept ? "accepted" : "rejected" });
        return accept;
    }

}
