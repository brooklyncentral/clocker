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

import brooklyn.entity.Entity;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.entity.group.zoneaware.BalancingNodePlacementStrategy;
import brooklyn.entity.trait.Identifiable;
import brooklyn.location.Location;
import brooklyn.location.docker.DockerHostLocation;
import brooklyn.util.collections.MutableList;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

/**
 * Placement strategy for Docker based on {@link BalancingNodePlacementStrategy}.
 *
 * @see BalancingNodePlacementStrategy
 */
public abstract class AbstractDockerPlacementStrategy extends BalancingNodePlacementStrategy implements DockerAwarePlacementStrategy {

    public static final Function<Identifiable, String> identity() { return identity; }

    private static final Function<Identifiable, String> identity = new Function<Identifiable, String>() {
        @Override
        public String apply(@Nullable Identifiable input) {
            return input.getClass().getSimpleName() + ":" + input.getId();
        }
    };

    protected DockerInfrastructure infrastructure;

    @Override
    public void init(List<DockerHostLocation> locations) {
        DockerHostLocation first = Iterables.get(locations, 0);
        infrastructure = first.getDockerInfrastructure();
    }

    @Override
    public final List<Location> locationsForAdditions(Multimap<Location, Entity> members, Collection<? extends Location> locations, int n) {
        if (locations.isEmpty() && n > 0) {
            throw new IllegalArgumentException("No locations supplied, when requesting locations for "+n+" nodes");
        } else if (n <= 0) {
        	return Lists.newArrayList();
        }

        List<DockerHostLocation> available = MutableList.copyOf(Iterables.filter(locations, DockerHostLocation.class));
        init(available);

        return getDockerHostLocations(members, available, n);
    }

    protected abstract List<Location> getDockerHostLocations(Multimap<Location, Entity> members, List<DockerHostLocation> available, int n);

    @Override
    public DockerInfrastructure getDockerInfrastructure() { return infrastructure; }

    @Override
    public Map<DockerHostLocation, Integer> toAvailableLocationSizes(Iterable<DockerHostLocation> locations) {
        Map<DockerHostLocation, Integer> result = Maps.newLinkedHashMap();
        for (DockerHostLocation host : locations) {
            result.put(host, host.getCurrentSize());
        }
        return result;
    }

    @Override
    public String toString() {
        return String.format("DockerAwarePlacementStrategy(%s)", getClass().getSimpleName());
    }

}
