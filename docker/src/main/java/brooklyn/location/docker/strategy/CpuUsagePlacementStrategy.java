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

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.container.docker.DockerAttributes;
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

    @Override
    public List<Location> locationsForAdditions(Multimap<Location, Entity> currentMembers, Collection<? extends Location> locs, int numToAdd) {
        if (locs.isEmpty() && numToAdd > 0) {
            throw new IllegalArgumentException("No locations supplied, when requesting locations for "+numToAdd+" nodes");
        }

        // Reject hosts over the allowed maximum CPU
        List<DockerHostLocation> available = Lists.newArrayList(Iterables.filter(locs, DockerHostLocation.class));
        for (DockerHostLocation machine : ImmutableList.copyOf(available)) {
            Double maxCpu = machine.getOwner().getConfig(DockerHost.DOCKER_CONTAINER_CLUSTER_MAX_CPU);
            Double currentCpu = machine.getOwner().getAttribute(DockerAttributes.CPU_USAGE);
            if (currentCpu < maxCpu) available.remove(machine);
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
        Preconditions.checkState(host != null, "Chosen Docker host was null; locs=%s", locs);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Placement for {} nodes in: {}", numToAdd, host);
        }

        // Add the newly created locations for each Docker host
        List<Location> result = Lists.newArrayList();
        result.addAll(Collections.<Location>nCopies(numToAdd, host.getDynamicLocation()));
        return result;
    }

    public Map<DockerHostLocation, Integer> toAvailableLocationSizes(Iterable<DockerHostLocation> locs) {
        Map<DockerHostLocation, Integer> result = Maps.newLinkedHashMap();
        for (DockerHostLocation loc : locs) {
            result.put(loc, loc.getOwner().getAttribute(DockerAttributes.CPU_USAGE).intValue());
        }
        return result;
    }

}
