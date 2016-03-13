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

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.objs.HasShortName;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.location.docker.DockerHostLocation;
import brooklyn.location.docker.DockerVirtualLocation;

/**
 * Placement strategy for Docker containers in host clusters.
 */
public interface DockerAwarePlacementStrategy extends HasShortName {

    @SetFromFlag("infrastructure")
    ConfigKey<DockerInfrastructure> DOCKER_INFRASTRUCTURE = DockerVirtualLocation.INFRASTRUCTURE;

    DockerInfrastructure getDockerInfrastructure();

    /**
     * Filters a list of {@link DockerHostLocation locations} to determine if the given {@link Entity} can be
     * deployed into a new container there.
     */
    List<DockerHostLocation> filterLocations(List<DockerHostLocation> locations, Entity context);

}
