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
package brooklyn.location.affinity;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.location.Location;
import brooklyn.location.cloud.AvailabilityZoneExtension;
import brooklyn.location.docker.DockerHostLocation;
import brooklyn.location.docker.DockerLocation;
import brooklyn.management.ManagementContext;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Docker host selection strategy using affinity rules to filter available hosts.
 */
public class DockerAffinityRuleStrategy implements AffinityRuleExtension {

    private static final Logger LOG = LoggerFactory.getLogger(DockerAffinityRuleStrategy.class);

    private final ManagementContext managementContext;
    private final DockerLocation location;

    public DockerAffinityRuleStrategy(ManagementContext managementContext, DockerLocation location) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
        this.location = Preconditions.checkNotNull(location, "location");
    }

    @Override
    public List<Location> filterLocations(Entity entity) {
        List<Location> hosts = location.getExtension(AvailabilityZoneExtension.class).getAllSubLocations();
        List<DockerHostLocation> available = Lists.newArrayList();

        // Select hosts that satisfy the affinity rules
        for (DockerHostLocation machine : Iterables.filter(hosts, DockerHostLocation.class)) {
            Optional<String> entityRules = Optional.fromNullable(entity.getConfig(DockerHost.DOCKER_HOST_AFFINITY_RULES));
            Optional<String> hostRules = Optional.fromNullable(machine.getOwner().getConfig(DockerHost.DOCKER_HOST_AFFINITY_RULES));
            Optional<String> infrastructureRules = Optional.fromNullable(machine.getOwner().getInfrastructure().getConfig(DockerHost.DOCKER_HOST_AFFINITY_RULES));
            String combined = Joiner.on('\n').join(Optional.presentInstances(ImmutableList.of(entityRules, hostRules, infrastructureRules)));
            AffinityRules rules = AffinityRules.rules(combined);

            Iterable<Entity> entities = managementContext.getEntityManager().findEntities(EntityPredicates.withLocation(machine));
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
        return ImmutableList.<Location>copyOf(available);
    }

}
