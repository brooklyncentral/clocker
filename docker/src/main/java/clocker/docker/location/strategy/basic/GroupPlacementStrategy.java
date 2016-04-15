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
package clocker.docker.location.strategy.basic;

import static clocker.docker.location.strategy.util.StrategyPredicates.childrenOf;
import static clocker.docker.location.strategy.util.StrategyPredicates.hasApplicationId;
import static clocker.docker.location.strategy.util.StrategyPredicates.nonEmpty;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.ThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clocker.docker.location.DockerHostLocation;
import clocker.docker.location.strategy.AbstractDockerPlacementStrategy;
import clocker.docker.location.strategy.DockerAwarePlacementStrategy;

import com.google.common.annotations.Beta;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;
import com.google.common.util.concurrent.Monitor;
import com.google.common.util.concurrent.Uninterruptibles;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.core.mutex.WithMutexes;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.time.Duration;

/**
 * A {@link DockerAwarePlacementStrategy strategy} that requires entities with
 * the <i>same</i> parent to use the same host and entities with a <i>different</i>
 * parent but in the same app use different hosts.
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

    @SetFromFlag("timeout")
    public static final ConfigKey<Duration> STRATEGY_TIMEOUT = ConfigKeys.newDurationConfigKey(
            "docker.strategy.timeout",
            "How long to wait for other entities using the strategy",
            Duration.minutes(20));

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

        try {
            acquireMutex(entity.getApplicationId(), "Filtering locations for " + entity);
        } catch (InterruptedException ie) {
            Exceptions.propagate(ie); // Should never happen...
        }

        // Find hosts with entities from our application deployed there
        Iterable<DockerHostLocation> sameApplication = Iterables.filter(available, hasApplicationId(entity.getApplicationId()));

        // Check if hosts have any deployed entities that share a parent with the input entity
        Optional<DockerHostLocation> sameParent = Iterables.tryFind(sameApplication, childrenOf(entity.getParent()));
        if (sameParent.isPresent()) {
            LOG.debug("Returning {} (same parent) for {} placement", sameParent.get(), entity);
            return ImmutableList.copyOf(sameParent.asSet());
        }

        // Remove hosts if they have any entities from our application deployed there
        Iterables.removeIf(available, hasApplicationId(entity.getApplicationId()));
        if (requireExclusive) {
            Iterables.removeIf(available, nonEmpty());
        }
        LOG.debug("Returning {} for {} placement", Iterables.toString(available), entity);
        return available;
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
        LOG.debug("Enter monitor {}: {}", mutexId, description);
        Monitor monitor = createMonitor(mutexId);
        Duration timeout = config().get(STRATEGY_TIMEOUT);
        monitor.enter(timeout.toMilliseconds(), TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean tryAcquireMutex(String mutexId, String description) {
        LOG.debug("Try to enter monitor {}: {}", mutexId, description);
        Monitor monitor = createMonitor(mutexId);
        return monitor.tryEnter();
    }

    @Override
    public void releaseMutex(String mutexId) {
        LOG.debug("Leaving monitor {}", mutexId);
        Monitor monitor = lookupMonitor(mutexId);
        if (monitor != null && monitor.isOccupiedByCurrentThread()) {
            monitor.leave();
        }
    }
}
