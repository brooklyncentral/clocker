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
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.location.Location;
import brooklyn.location.docker.DockerHostLocation;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;

/**
 * Placement strategy that adds containers to Docker hosts until they run out of capacity.
 */
public class DepthFirstPlacementStrategy extends AbstractDockerPlacementStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(DepthFirstPlacementStrategy.class);

    @Override
    public List<Location> locationsForAdditions(Multimap<Location, Entity> currentMembers, Collection<? extends Location> locs, int numToAdd) {
        if (locs.isEmpty() && numToAdd > 0) {
            throw new IllegalArgumentException("No locations supplied, when requesting locations for "+numToAdd+" nodes");
        }

        List<DockerHostLocation> available = Lists.newArrayList(Iterables.filter(locs, DockerHostLocation.class));
        int remaining = numToAdd;
        for (DockerHostLocation machine : available) {
            int maxSize = machine.getOwner().getConfig(DockerHost.DOCKER_CONTAINER_CLUSTER_MAX_SIZE);
            int currentSize = machine.getOwner().getCurrentSize();
            remaining -= (maxSize - currentSize);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Requested {}, Need {} more from new Docker hosts, current hosts {}",
                    new Object[] { numToAdd, remaining, Iterables.toString(Iterables.transform(locs, identity())) });
        }

        if (remaining > 0) {
            // Grow the Docker host cluster; based on max number of Docker containers
            int maxSize = getDockerInfrastructure().getConfig(DockerInfrastructure.DOCKER_CONTAINER_CLUSTER_MAX_SIZE);
            int delta = (remaining / maxSize) + (remaining % maxSize > 0 ? 1 : 0);
            Collection<Entity> added = getDockerInfrastructure().getDockerHostCluster().resizeByDelta(delta);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Added {} Docker hosts: {}", delta, Iterables.toString(Iterables.transform(added, identity())));
            }

            // Wait until all new Docker hosts have started up
            for (Entity each : added) Entities.waitForServiceUp(each);

            // Add the newly created locations for each Docker host
            Collection<DockerHostLocation> dockerHosts = Collections2.transform(added, new Function<Entity, DockerHostLocation>() {
                @Override
                public DockerHostLocation apply(@Nullable Entity input) {
                    return ((DockerHost) input).getDynamicLocation();
                }
            });
            available.addAll(dockerHosts);
        }

        // Logic from parent, with enhancements and types
        List<Location> result = Lists.newArrayList();
        Map<DockerHostLocation, Integer> sizes = toAvailableLocationSizes(available);
        for (int i = 0; i < numToAdd; i++) {
            DockerHostLocation largest = null;
            int maxSize = 0;
            for (DockerHostLocation loc : sizes.keySet()) {
                int size = sizes.get(loc);
                if (largest == null || size > maxSize) {
                    largest = loc;
                    maxSize = size;
                }
            }
            Preconditions.checkState(largest != null, "Largest was null; locs=%s", sizes.keySet());
            result.add(largest);

            // Update population in locations, removing if maximum reached
            int currentSize = sizes.get(largest) + 1;
            if (currentSize < largest.getMaxSize()) {
                sizes.put(largest, currentSize);
            } else {
                sizes.remove(largest);
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Placement for {} nodes: {}", numToAdd, Iterables.toString(Iterables.transform(result, identity())));
        }
        return result;
    }

}
