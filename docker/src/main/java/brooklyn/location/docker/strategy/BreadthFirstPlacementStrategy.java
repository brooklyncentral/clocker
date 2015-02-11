/*
 * Copyright 2014-2015 by Cloudsoft Corporation Limited
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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.location.docker.DockerHostLocation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * Placement strategy that adds containers to each Docker host in turn.
 */
public class BreadthFirstPlacementStrategy extends AbstractDockerPlacementStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(BreadthFirstPlacementStrategy.class);

    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public List<DockerHostLocation> filterLocations(List<DockerHostLocation> locations, Entity context) {
        if (locations == null || locations.isEmpty()) {
            return ImmutableList.of();
        }

        int size = Iterables.size(locations);
        int next = counter.incrementAndGet() % size;
        if (LOG.isDebugEnabled()) {
            LOG.debug("Breadth first strategy using {} of {}", next, size);
        }

        return ImmutableList.copyOf(Iterables.concat(Iterables.skip(locations, next), Iterables.limit(locations, next)));
    }
}
