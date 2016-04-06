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
import com.google.common.base.Function;
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
public class GroupPlacementStrategy extends AbstractDockerPlacementStrategy implements WithMutexes, Predicate<DockerHostLocation> {

    private static final Logger LOG = LoggerFactory.getLogger(GroupPlacementStrategy.class);

    private static final Object lock = new Object[0];

    private Entity entity;

    @SetFromFlag("exclusive")
    public static final ConfigKey<Boolean> REQUIRE_EXCLUSIVE = ConfigKeys.newBooleanConfigKey(
            "docker.constraint.exclusive",
            "Whether the Docker host must be exclusive to this application; by default other applications can co-exist",
            Boolean.FALSE);

    @SetFromFlag("monitorMap")
    public static final ConfigKey<Map<String, Monitor>> MONITOR_MAP = ConfigKeys.newConfigKey(
            new TypeToken<Map<String, Monitor>>() { },
            "groupPlacementStrategy.map.monitors",
            "A mapping from application IDs to monitors; used to synchronize threads during strategy execution.",
            MutableMap.<String, Monitor>of());

    @Override
    public List<DockerHostLocation> filterLocations(List<DockerHostLocation> locations, Entity context) {
        if (locations == null || locations.isEmpty()) {
            return ImmutableList.of();
        }
        entity = context;
        if (getDockerInfrastructure() == null) config().set(DOCKER_INFRASTRUCTURE, Iterables.getLast(locations).getDockerInfrastructure());
        List<DockerHostLocation> available = MutableList.copyOf(locations);

        Monitor monitor = null;
        boolean found = false;
        synchronized (lock) {
            monitor = lookupMonitor(entity.getApplicationId());
            found = (monitor != null);
            if (!found) {
                createMonitor(entity.getApplicationId());
            }
        }
        if (found) {
            monitor.enter();
        }

        Optional<DockerHostLocation> chosen = Iterables.tryFind(available, this);
        return ImmutableList.copyOf(chosen.asSet());
    }

    @Override
    public boolean apply(DockerHostLocation input) {
        boolean requireExclusive = config().get(REQUIRE_EXCLUSIVE);
        Iterable<Entity> deployed = Iterables.filter(Iterables.transform(input.getDockerContainerList(),
                new Function<Entity, Entity>() {
                    @Override
                    public Entity apply(Entity input) {
                        return entity.sensors().get(DockerContainer.ENTITY);
                    }
                }), Predicates.notNull());
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

    /**
     * Look up the {@link Monitor} for an application.
     *
     * @return {@code null} if the monitor has not been created yet
     */
    protected Monitor lookupMonitor(String mutexId) {
        synchronized (lock) {
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
        synchronized (lock) {
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
        synchronized (lock) {
            Monitor monitor = createMonitor(mutexId);
            monitor.enter();
        }
    }

    @Override
    public boolean tryAcquireMutex(String mutexId, String description) {
        synchronized (lock) {
            Monitor monitor = createMonitor(mutexId);
            return monitor.tryEnter();
        }
    }

    @Override
    public void releaseMutex(String mutexId) {
        synchronized (lock) {
            Monitor monitor = lookupMonitor(mutexId);
            if (monitor != null && monitor.isOccupiedByCurrentThread()) {
                monitor.leave();
            }
        }
    }
}
