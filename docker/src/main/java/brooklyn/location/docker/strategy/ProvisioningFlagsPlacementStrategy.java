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

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.HardwareDetails;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.entity.EntityPredicates;
import org.apache.brooklyn.core.location.cloud.CloudLocationConfig;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;

import brooklyn.location.docker.DockerHostLocation;

/**
 * Placement strategy that selects the Docker host with the lowest CPU usage.
 */
public class ProvisioningFlagsPlacementStrategy extends AbstractDockerPlacementStrategy implements DockerAwareProvisioningStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(ProvisioningFlagsPlacementStrategy.class);

    public static final ConfigKey<Object> MIN_RAM = CloudLocationConfig.MIN_RAM;
    public static final ConfigKey<Integer> MIN_CORES = CloudLocationConfig.MIN_CORES;

    @Override
    public List<DockerHostLocation> filterLocations(List<DockerHostLocation> locations, Entity context) {
        if (locations == null || locations.isEmpty()) {
            return ImmutableList.of();
        }

        Integer strategyMinRam = (Integer) config().get(MIN_RAM);
        Integer strategyMinCores = config().get(MIN_CORES);

        Map<String,Object> contextFlags = context.config().get(SoftwareProcess.PROVISIONING_PROPERTIES);
        if (contextFlags == null || contextFlags.isEmpty()) return locations;
        Integer contextMinRam = (Integer) contextFlags.get("minRam");
        Integer contextMinCores = (Integer) contextFlags.get("minCores");

        int minRam = max(strategyMinRam, contextMinRam);
        int minCores = max(strategyMinCores, contextMinCores);
        LOG.info("Provisioning strategy RAM {}, cores {}", minRam, minCores);

        List<DockerHostLocation> available = MutableList.of();
        for (DockerHostLocation location : locations) {
            HardwareDetails details = location.getMachine().getMachineDetails().getHardwareDetails();
            if (details.getCpuCount() > minCores && details.getRam() > minRam) {
                // Look up other entities already using this location and their requirements
                Iterable<Entity> entities = getBrooklynManagementContext().getEntityManager().findEntities(EntityPredicates.locationsIncludes(location));
                int ramUsed = 0, coresUsed = 0;
                for (Entity entity : entities) {
                    Map<String,Object> entityFlags = entity.config().get(SoftwareProcess.PROVISIONING_PROPERTIES);
                    LOG.info("Checking provisioning flags on {}: {}", entity, entityFlags);
                    if (entityFlags == null || entityFlags.isEmpty()) continue;
                    Integer entityMinRam = (Integer) entityFlags.get("minRam");
                    Integer entityMinCores = (Integer) entityFlags.get("minCores");
                    ramUsed += entityMinRam == null ? 0 : entityMinRam;
                    coresUsed += entityMinCores == null ? 0 : entityMinCores;
                }
                if ((details.getCpuCount() - coresUsed) > minCores && (details.getRam() - ramUsed) > minRam) {
                    available.add(location);
                }
            }
        }
        return ImmutableList.copyOf(available);
    }

    @Override
    public Map<String,Object> apply(Map<String,Object> contextFlags) {
        Integer strategyMinRam = (Integer) config().get(MIN_RAM);
        Integer strategyMinCores = config().get(MIN_CORES);

        Map<String,Object> provisioningFlags;
        if (contextFlags != null) {
            provisioningFlags = MutableMap.copyOf(contextFlags);
        } else {
            provisioningFlags = Maps.newLinkedHashMap();
        }
        Integer contextMinRam = (Integer) provisioningFlags.get("minRam");
        Integer contextMinCores = (Integer) provisioningFlags.get("minCores");

        int minRam = max(strategyMinRam, contextMinRam);
        int minCores = max(strategyMinCores, contextMinCores);

        provisioningFlags.put("minRam", minRam);
        provisioningFlags.put("minCores", minCores);
        return provisioningFlags;
    }

    private Integer max(Integer one, Integer two) {
        return Math.max(one == null ? 0 : one, two == null ? 0 : two);
    }

}
