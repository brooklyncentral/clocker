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

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.core.entity.EntityPredicates;

import brooklyn.location.docker.DockerHostLocation;

/**
 * Strategy that requires the hostname of the Docker location to match a particular regexp.
 */
public class GroupPlacementStrategy extends BasicDockerPlacementStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(GroupPlacementStrategy.class);

    @Override
    public boolean apply(DockerHostLocation input) {
        List<Entity> deployed = input.getDockerContainerList();
        Entity parent = entity.getParent();
        String applicationId = entity.getApplicationId();
        Iterable<Entity> sameApplication = Iterables.filter(deployed, EntityPredicates.applicationIdEqualTo(applicationId));
        if (Iterables.isEmpty(sameApplication)) {
            // There are no entites from the same app here
            return true;
        } else {
            Iterable<Entity> sameParent = Iterables.filter(sameApplication, EntityPredicates.isChildOf(parent));
            if (Iterables.isEmpty(sameParent)) {
                // There are entites from the same app, but different parent here
                return false;
            } else {
                // The entites here have the same parent
                return true;
            }
        }
    }
}
