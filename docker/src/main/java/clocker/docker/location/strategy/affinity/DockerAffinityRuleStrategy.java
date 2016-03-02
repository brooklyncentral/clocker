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
package clocker.docker.location.strategy.affinity;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clocker.docker.entity.DockerHost;
import clocker.docker.location.DockerHostLocation;
import clocker.docker.location.strategy.AbstractDockerPlacementStrategy;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.core.entity.EntityPredicates;

/**
 * Docker host selection strategy using affinity rules to filter available hosts.
 */
public class DockerAffinityRuleStrategy extends AbstractDockerPlacementStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(DockerAffinityRuleStrategy.class);

    @Override
    public List<DockerHostLocation> filterLocations(List<DockerHostLocation> locations, Entity entity) {
        List<DockerHostLocation> available = Lists.newArrayList();

        // Select hosts that satisfy the affinity rules
        for (DockerHostLocation machine : locations) {
            Optional<List<String>> entityRules = Optional.fromNullable(entity.config().get(DockerHost.DOCKER_HOST_AFFINITY_RULES));
            Optional<List<String>> hostRules = Optional.fromNullable(machine.getOwner().config().get(DockerHost.DOCKER_HOST_AFFINITY_RULES));
            Optional<List<String>> infrastructureRules = Optional.fromNullable(machine.getOwner().getInfrastructure().config().get(DockerHost.DOCKER_HOST_AFFINITY_RULES));
            Iterable<String> combined = Iterables.concat(Optional.presentInstances(ImmutableList.of(entityRules, hostRules, infrastructureRules)));
            AffinityRules rules = AffinityRules.rulesFor(entity).parse(combined);

            Iterable<Entity> entities = getBrooklynManagementContext().getEntityManager().findEntities(EntityPredicates.locationsIncludes(machine));
            if (Iterables.isEmpty(entities)) {
                if (rules.allowEmptyLocations()) {
                    available.add(machine);
                }
            } else {
                Iterable<Entity> filtered = Iterables.filter(entities, rules);
                if (Iterables.size(filtered) == Iterables.size(entities)) {
                    available.add(machine);
                }
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Available Docker hosts: {}", Iterables.toString(available));
        }
        return available;
    }

}
