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
package clocker.docker.location.strategy.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clocker.docker.entity.container.DockerContainer;
import clocker.docker.location.DockerHostLocation;

import com.google.common.annotations.Beta;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.core.entity.EntityFunctions;
import org.apache.brooklyn.core.entity.EntityPredicates;

/**
 * A number of {@link Predicate predicates} that check for properties of
 * a {@link DockerHostLocation}.
 *
 * @since 1.1.0
 */
@Beta
public class StrategyPredicates {

    private static final Logger LOG = LoggerFactory.getLogger(StrategyPredicates.class);

    /* Do not instantiate. */
    private StrategyPredicates() { }

    /** @see {@link ChildrenOfPredicate} */
    public static Predicate<DockerHostLocation> childrenOf(Entity parent) {
        return new ChildrenOfPredicate(parent);
    }

    /**
     * A {@link Predicate} that checks if a {@link DockerHostLocation host} contains
     * entities that are children of the given parent.
     */
    public static class ChildrenOfPredicate implements Predicate<DockerHostLocation> {
        private Entity parent;

        public ChildrenOfPredicate(Entity parent) {
            this.parent = parent;
        }

        @Override
        public boolean apply(DockerHostLocation input) {
            Iterable<Entity> deployed = Iterables.filter(
                    Iterables.transform(input.getDockerContainerList(), EntityFunctions.config(DockerContainer.ENTITY.getConfigKey())),
                    Predicates.notNull());

            Iterable<Entity> sameParent = Iterables.filter(deployed, EntityPredicates.isChildOf(parent));
            if (Iterables.isEmpty(sameParent)) {
                LOG.debug("No entities with parent {} on {}", parent, input );
                return false;
            } else {
                LOG.debug("Found entities with parent {} on {}", parent, input );
                return true;
            }
        }
    }

    /** @see {@link HasApplicationIdPredicate} */
    public static Predicate<DockerHostLocation> hasApplicationId(String applicationId) {
        return new HasApplicationIdPredicate(applicationId);
    }

    /**
     * A {@link Predicate} that checks if a {@link DockerHostLocation host} contains
     * entities that have the specified application ID.
     */
    public static class HasApplicationIdPredicate implements Predicate<DockerHostLocation> {
        private String applicationId;

        public HasApplicationIdPredicate(String applicationId) {
            this.applicationId = applicationId;
        }

        @Override
        public boolean apply(DockerHostLocation input) {
            Iterable<Entity> deployed = Iterables.filter(
                    Iterables.transform(input.getDockerContainerList(), EntityFunctions.config(DockerContainer.ENTITY.getConfigKey())),
                    Predicates.notNull());

            Iterable<Entity> sameApplication = Iterables.filter(deployed, EntityPredicates.applicationIdEqualTo(applicationId));
            if (Iterables.isEmpty(sameApplication)) {
                LOG.debug("No entities with application id {} on {}", applicationId, input);
                return false;
            } else {
                LOG.debug("Found entities with application id {} on {}", applicationId, input);
                return true;
            }
        }
    }

    /** @see {@link NonEmptyPredicate} */
    public static Predicate<DockerHostLocation> nonEmpty() {
        return new NonEmptyPredicate();
    }

    /**
     * A {@link Predicate} that checks if a {@link DockerHostLocation host} has
     * deployed entities.
     */
    public static class NonEmptyPredicate implements Predicate<DockerHostLocation> {
        @Override
        public boolean apply(DockerHostLocation input) {
            Iterable<Entity> deployed = Iterables.filter(
                    Iterables.transform(input.getDockerContainerList(), EntityFunctions.config(DockerContainer.ENTITY.getConfigKey())),
                    Predicates.notNull());

            return Iterables.size(deployed) > 0;
        }
    }

}
