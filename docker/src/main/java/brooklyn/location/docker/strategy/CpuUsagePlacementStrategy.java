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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.location.Location;
import brooklyn.location.docker.DockerHostLocation;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

/**
 * Placement strategy that selects the Docker host with the lowest CPU usage.
 */
public class CpuUsagePlacementStrategy extends AbstractDockerPlacementStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(CpuUsagePlacementStrategy.class);

    public static final ConfigKey<Double> DOCKER_CONTAINER_CLUSTER_MAX_CPU = ConfigKeys.newDoubleConfigKey("docker.container.cluster.maxCpu",
            "Maximum CPU usage across a Docker container cluster", 0.5d);

    @Override
    protected List<Location> getDockerHostLocations(Multimap<Location, Entity> members, List<DockerHostLocation> available, int n) {
        // Reject hosts over the allowed maximum CPU
        for (DockerHostLocation machine : ImmutableList.copyOf(available)) {
            Double maxCpu = machine.getOwner().getConfig(DOCKER_CONTAINER_CLUSTER_MAX_CPU);
            Double currentCpu = machine.getOwner().getAttribute(DockerHost.CPU_USAGE);
            if (currentCpu > maxCpu) available.remove(machine);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Available Docker hosts: {}", Iterables.toString(available));
        }

        DockerHost host = null;
        if (available.isEmpty()) {
            // Grow the Docker host cluster
            Collection<Entity> added = getDockerInfrastructure().getDockerHostCluster().resizeByDelta(1);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Added Docker host: {}", Iterables.toString(added));
            }
            host = (DockerHost) Iterables.getOnlyElement(added);

            // Wait until new Docker host has started up
            Entities.waitForServiceUp(host);
        } else {
            // Choose the lowest CPU usage from available
            Map<DockerHostLocation, Integer> sizes = toAvailableLocationSizes(available);
            int minCpu = 100;
            for (DockerHostLocation loc : sizes.keySet()) {
                int usage = sizes.get(loc);
                if (host == null || usage < minCpu) {
                    host = loc.getOwner();
                    minCpu = usage;
                }
            }
        }
        Preconditions.checkState(host != null, "Chosen Docker host was null; locs=%s", available);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Placement for {} nodes in: {}", n, host);
        }

        // Add the newly created locations for each Docker host
        List<Location> result = Lists.newArrayList();
        result.addAll(Collections.<Location>nCopies(n, host.getDynamicLocation()));
        return result;
    }

    public Map<DockerHostLocation, Integer> toAvailableLocationSizes(Iterable<DockerHostLocation> locations) {
        Map<DockerHostLocation, Integer> result = Maps.newLinkedHashMap();
        for (DockerHostLocation host : locations) {
            result.put(host, host.getOwner().getAttribute(DockerHost.CPU_USAGE).intValue());
        }
        return result;
    }

}
