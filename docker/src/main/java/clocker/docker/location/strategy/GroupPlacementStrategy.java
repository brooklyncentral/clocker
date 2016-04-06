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
package clocker.docker.location.strategy;

import java.util.List;
import java.util.Map;

import javax.annotation.concurrent.ThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clocker.docker.entity.container.DockerContainer;
import clocker.docker.location.DockerHostLocation;

import com.google.common.annotations.Beta;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;
import com.google.common.util.concurrent.Monitor;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.EntityFunctions;
import org.apache.brooklyn.core.entity.EntityPredicates;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.core.mutex.WithMutexes;

/**
 * A {@link DockerAwarePlacementStrategy strategy} that requires entities with
 * the same parent to use the same host.
 *
 * Can be configured to require exclusive use of the host with the
 * {@link #REQUIRE_EXCLUSIVE exclusive} ({@code docker.constraint.exclusive})
 * option set to {@code true}; normally {@code false}.
 *
 * @since 1.1.0
 */
@Beta
@ThreadSafe
public class GroupPlacementStrategy extends AbstractDockerPlacementStrategy implements WithMutexes {

    private static final Logger LOG = LoggerFactory.getLogger(GroupPlacementStrategy.class);
    private static final Object LOCK = new Object[0];

    @SetFromFlag("exclusive")
    public static final ConfigKey<Boolean> REQUIRE_EXCLUSIVE = ConfigKeys.newBooleanConfigKey(
            "docker.constraint.exclusive",
            "Whether the Docker host must be exclusive to this application; by default other applications can co-exist",
            Boolean.FALSE);

    public static final ConfigKey<Map<String, Monitor>> MONITOR_MAP = ConfigKeys.newConfigKey(
            new TypeToken<Map<String, Monitor>>() { },
            "groupPlacementStrategy.map.monitors",
            "A mapping from application IDs to monitors; used to synchronize threads during strategy execution.");

    @Override
    public List<DockerHostLocation> filterLocations(List<DockerHostLocation> locations, Entity entity) {
        if (locations == null || locations.isEmpty()) {
            return ImmutableList.of();
        }
        if (getDockerInfrastructure() == null) config().set(DOCKER_INFRASTRUCTURE, Iterables.getLast(locations).getDockerInfrastructure());
        List<DockerHostLocation> available = MutableList.copyOf(locations);
        boolean requireExclusive = config().get(REQUIRE_EXCLUSIVE);

        Monitor monitor = createMonitor(entity.getApplicationId());
        monitor.enter();
        LOG.debug("Placing {} from {} with parent {}", new Object[] { entity, entity.getApplicationId(), entity.getParent() });

        // Find hosts with entities from our application deployed there
        Iterable<DockerHostLocation> sameApplication = Iterables.filter(available, hasApplicationId(entity.getApplicationId()));

        // Check if hosts have any deployed entities that share a parent with the input entity
        Optional<DockerHostLocation> sameParent = Iterables.tryFind(sameApplication, childrenOf(entity.getParent()));
        if (sameParent.isPresent()) {
            LOG.debug("Returning {} for {} placement", sameParent.get(), entity);
            return ImmutableList.copyOf(sameParent.asSet());
        }

        // Remove hosts if they do not have any entities from our application deployed there
        Iterables.removeIf(available, hasApplicationId(entity.getApplicationId()));
        if (requireExclusive) {
            Iterables.removeIf(available, nonEmpty());
        }
        LOG.debug("Returning {} for {} placement", Iterables.toString(available), entity);
        return available;
    }

    private static Predicate<DockerHostLocation> childrenOf(Entity parent) {
        return new ChildrenOfPredicate(parent);
    }

    private static class ChildrenOfPredicate implements Predicate<DockerHostLocation> {
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
            LOG.debug("Found {} deployed to {} with parent {}",
                    new Object[] { Iterables.toString(sameParent), input, parent });

            if (Iterables.isEmpty(sameParent)) {
                LOG.debug("No entities with parent {} on {}", parent, input );
                return false;
            } else {
                LOG.debug("Found entities with parent {} on {}", parent, input );
                return true;
            }
        }
    }

    private Predicate<DockerHostLocation> hasApplicationId(String applicationId) {
        return new HasApplicationIdPredicate(applicationId);
    }

    private class HasApplicationIdPredicate implements Predicate<DockerHostLocation> {
        private String applicationId;

        public HasApplicationIdPredicate(String applicationId) {
            this.applicationId = applicationId;
        }

        @Override
        public boolean apply(DockerHostLocation input) {
            Iterable<Entity> deployed = Iterables.filter(
                    Iterables.transform(input.getDockerContainerList(), EntityFunctions.config(DockerContainer.ENTITY.getConfigKey())),
                    Predicates.notNull());
            LOG.debug("Found {} containers on {}", Iterables.toString(input.getDockerContainerList()), input);
            LOG.debug("Found {} entities deployed to {}", Iterables.toString(deployed), input);

            Iterable<Entity> sameApplication = Iterables.filter(deployed, EntityPredicates.applicationIdEqualTo(applicationId));
            LOG.debug("Found {} deployed to {} with application id {}",
                    new Object[] { Iterables.toString(sameApplication), input, applicationId });

            if (Iterables.isEmpty(sameApplication)) {
                LOG.debug("No entities with application id {} on {}", applicationId, input);
                return false;
            } else {
                LOG.debug("Found entities with application id {} on {}", applicationId, input);
                return true;
            }
        }
    }

    private Predicate<DockerHostLocation> nonEmpty() {
        return new NonEmptyPredicate();
    }

    private class NonEmptyPredicate implements Predicate<DockerHostLocation> {
        @Override
        public boolean apply(DockerHostLocation input) {
            Iterable<Entity> deployed = Iterables.filter(
                    Iterables.transform(input.getDockerContainerList(), EntityFunctions.config(DockerContainer.ENTITY.getConfigKey())),
                    Predicates.notNull());

            return !Iterables.isEmpty(deployed);
        }
    }

    /**
     * Look up the {@link Monitor} for an application.
     *
     * @return {@code null} if the monitor has not been created yet
     */
    protected Monitor lookupMonitor(String mutexId) {
        synchronized (LOCK) {
            Map<String, Monitor> map = getDockerInfrastructure().config().get(MONITOR_MAP);
            return (map != null) ? map.get(mutexId) : null;
        }
    }

    /**
     * Create a new {@link Monitor} and optionally the {@link #MONITOR_MAP map}
     * for an application. Uses existing monitor if it already exists.
     *
     * @return The monitor for the scope that is stored in the {@link Map map}.
     */
    protected Monitor createMonitor(String mutexId) {
        synchronized (LOCK) {
            Map<String, Monitor> map = getDockerInfrastructure().config().get(MONITOR_MAP);
            if (map == null) {
                map = MutableMap.<String, Monitor>of();
            }

            if (!map.containsKey(mutexId)) {
                map.put(mutexId, new Monitor());
                getDockerInfrastructure().config().set(MONITOR_MAP, map);
            }
            Monitor monitor = map.get(mutexId);

            return monitor;
        }
    }

    @Override
    public boolean hasMutex(String mutexId) { throw new UnsupportedOperationException(); }

    @Override
    public void acquireMutex(String mutexId, String description) throws InterruptedException {
        Monitor monitor = createMonitor(mutexId);
        monitor.enter();
    }

    @Override
    public boolean tryAcquireMutex(String mutexId, String description) {
        Monitor monitor = createMonitor(mutexId);
        return monitor.tryEnter();
    }

    @Override
    public void releaseMutex(String mutexId) {
        Monitor monitor = lookupMonitor(mutexId);
        if (monitor != null && monitor.isOccupiedByCurrentThread()) {
            monitor.leave();
        }
    }
}
