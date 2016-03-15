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

import java.util.Map;
import java.util.concurrent.Semaphore;

import javax.annotation.concurrent.ThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.EntityPredicates;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.core.mutex.SemaphoreWithOwners;
import org.apache.brooklyn.util.core.mutex.WithMutexes;
import org.apache.brooklyn.util.core.task.Tasks;

import brooklyn.location.docker.DockerHostLocation;

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
public class GroupPlacementStrategy extends BasicDockerPlacementStrategy implements WithMutexes {

    private static final Logger LOG = LoggerFactory.getLogger(GroupPlacementStrategy.class);

    private static final Object lock = new Object[0];

    @SetFromFlag("exclusive")
    public static final ConfigKey<Boolean> REQUIRE_EXCLUSIVE = ConfigKeys.newBooleanConfigKey(
            "docker.constraint.exclusive",
            "Whether the Docker host must be exclusive to this application; by default other applications can co-exist",
            Boolean.FALSE);

    @SetFromFlag("semaphoreMap")
    public static final ConfigKey<Map<String, Semaphore>> SEMAPHORE_MAP = ConfigKeys.newConfigKey(
            new TypeToken<Map<String, Semaphore>>() { },
            "groupPlacementStrategy.map.semaphores",
            "A mapping from application IDs to Semaphores; used to synchronize threads during strategy execution.",
            MutableMap.<String, Semaphore>of());

    @Override
    public boolean apply(DockerHostLocation input) {
        if (getDockerInfrastructure() == null) config().set(DOCKER_INFRASTRUCTURE, input.getDockerInfrastructure());
        LOG.debug("GPS@{} apply({}) for {}/{}", new Object[] { Tasks.current().getId(), input.getMachine().getHostname(), entity.getId(), entity.getCatalogItemId() });
        synchronized (lock) {
            Semaphore semaphore = lookupSemaphore(entity.getApplicationId());
            if (semaphore == null) {
                LOG.debug("GPS@{} semaphore is null", new Object[] { Tasks.current().getId() });
                createSemaphore(entity.getApplicationId());
            } else {
                LOG.debug("GPS@{} semaphore found ({}) acquiring", new Object[] { Tasks.current().getId(), semaphore.availablePermits() });
                semaphore.acquireUninterruptibly();
                LOG.debug("GPS@{} semaphore acquired", new Object[] { Tasks.current().getId() });
            }
        }

        boolean requireExclusive = config().get(REQUIRE_EXCLUSIVE);
        String dockerApplicationId = input.getOwner().getApplicationId();
        Iterable<Entity> deployed = Iterables.filter(input.getDockerContainerList(), Predicates.not(EntityPredicates.applicationIdEqualTo(dockerApplicationId)));
        Entity parent = entity.getParent();
        String applicationId = entity.getApplicationId();

        try {
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
        } finally { LOG.debug("GPS@{} apply({}) done", new Object[] { Tasks.current().getId(), input.getMachine().getHostname() }); }
    }

    /**
     * Look up the {@link Semaphore} for an application.
     *
     * @return {@code null} if the semaphore has not been created yet
     */
    protected Semaphore lookupSemaphore(String mutexId) {
        synchronized (lock) {
            Map<String, Semaphore> map = getDockerInfrastructure().config().get(SEMAPHORE_MAP);
            return (map != null) ? map.get(mutexId) : null;
        }
    }

    /**
     * Create a new {@link Semaphore} and optionally the {@link #SEMAPHORE_MAP map}
     * for an application. Uses existing semaphore if it already exists.
     *
     * @return The semaphore for the scope that is stored in the {@link Map map}.
     */
    protected Semaphore createSemaphore(String mutexId) {
        synchronized (lock) {
            Map<String, Semaphore> map = getDockerInfrastructure().config().get(SEMAPHORE_MAP);
            if (map == null) {
                map = MutableMap.<String, Semaphore>of();
            }

            if (!map.containsKey(mutexId)) {
                map.put(mutexId, new Semaphore(1));
            }
            Semaphore semaphore = map.get(mutexId);
            getDockerInfrastructure().config().set(SEMAPHORE_MAP, map);

            return semaphore;
        }
    }

    @Override
    public boolean hasMutex(String mutexId) { throw new UnsupportedOperationException(); }

    @Override
    public void acquireMutex(String mutexId, String description) throws InterruptedException {
        synchronized (lock) {
            Semaphore semaphore = createSemaphore(mutexId);
            semaphore.acquire(); 
        }
    }

    @Override
    public boolean tryAcquireMutex(String mutexId, String description) {
        synchronized (lock) {
            Semaphore semaphore = createSemaphore(mutexId);
            return semaphore.tryAcquire();
        }
    }

    @Override
    public void releaseMutex(String mutexId) {
        synchronized (lock) {
            Semaphore semaphore = lookupSemaphore(mutexId);
            if (semaphore != null) semaphore.release();
        }
    }
}
