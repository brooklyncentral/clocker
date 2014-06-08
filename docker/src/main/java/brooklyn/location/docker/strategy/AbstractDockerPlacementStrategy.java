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

import java.util.Map;

import javax.annotation.Nullable;

import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.entity.group.zoneaware.BalancingNodePlacementStrategy;
import brooklyn.entity.trait.Identifiable;
import brooklyn.location.docker.DockerHostLocation;
import brooklyn.util.config.ConfigBag;

import com.google.common.base.Function;
import com.google.common.collect.Maps;

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
    public void init(ConfigBag setup) {
        infrastructure = setup.get(DOCKER_INFRASTRUCTURE);
    }

    @Override
    public DockerInfrastructure getDockerInfrastructure() { return infrastructure; }

    // TODO extract common code from locationsForAdditions method

    @Override
    public Map<DockerHostLocation, Integer> toAvailableLocationSizes(Iterable<DockerHostLocation> locs) {
        Map<DockerHostLocation, Integer> result = Maps.newLinkedHashMap();
        for (DockerHostLocation loc : locs) {
            result.put(loc, loc.getCurrentSize());
        }
        return result;
    }

    @Override
    public String toString() {
        return "Docker NodePlacementStrategy";
    }

}
