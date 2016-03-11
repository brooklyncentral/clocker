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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.EntityPredicates;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import brooklyn.location.docker.DockerHostLocation;

/**
 * Strategy that requires entities with the same parent to use the same host.
 * Can be configured to require exclusive use of the host with the
 * {@code exclusive} option.
 */
public class GroupPlacementStrategy extends BasicDockerPlacementStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(GroupPlacementStrategy.class);

    @SetFromFlag("requireEmpty")
    public static final ConfigKey<Boolean> REQUIRE_EXCLUSIVE = ConfigKeys.newBooleanConfigKey(
            "docker.constraint.exclusive",
            "Whether the Docker host must be exclusive to this application; by default other applications can co-exist",
            Boolean.FALSE);

    @Override
    public boolean apply(DockerHostLocation input) {
        boolean requireExclusive = config().get(REQUIRE_EXCLUSIVE);
        String dockerApplicationId = input.getOwner().getApplicationId();
        Iterable<Entity> deployed = Iterables.filter(input.getDockerContainerList(), Predicates.not(EntityPredicates.applicationIdEqualTo(dockerApplicationId)));
        Entity parent = entity.getParent();
        String applicationId = entity.getApplicationId();
        Iterable<Entity> sameApplication = Iterables.filter(deployed, EntityPredicates.applicationIdEqualTo(applicationId));
        if (requireExclusive && Iterables.size(deployed) > Iterables.size(sameApplication)) {
            LOG.debug("Found entities not in {}; required exclusive. Reject: {}", applicationId, input);
            return false;
        }
        if (Iterables.isEmpty(sameApplication)) {
            LOG.debug("No entities present from {}. Accept: {}", applicationId, input);
            return true;
        } else {
            Iterable<Entity> sameParent = Iterables.filter(sameApplication, EntityPredicates.isChildOf(parent));
            if (Iterables.isEmpty(sameParent)) {
                LOG.debug("All entities from {} have different parent. Reject: {}", applicationId, input);
                return false;
            } else {
                LOG.debug("All entities from {} have same parent. Accept: {}", applicationId, input);
                return true;
            }
        }
    }
}
