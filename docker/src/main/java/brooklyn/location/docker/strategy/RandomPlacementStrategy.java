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

import java.util.Collections;
import java.util.List;

import com.google.common.collect.ImmutableList;

import org.apache.brooklyn.api.entity.Entity;

import brooklyn.location.docker.DockerHostLocation;
import brooklyn.util.collections.MutableList;

/**
 * Placement strategy that randomises the list of hosts.
 */
public class RandomPlacementStrategy extends AbstractDockerPlacementStrategy {

    @Override
    public List<DockerHostLocation> filterLocations(List<DockerHostLocation> locations, Entity context) {
        if (locations == null || locations.isEmpty()) {
            return ImmutableList.of();
        }

        List<DockerHostLocation> copy = MutableList.copyOf(locations);
        Collections.shuffle(copy);


        return ImmutableList.copyOf(copy);
    }
}
