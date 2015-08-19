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

import org.apache.brooklyn.core.objs.BasicConfigurableObject;

import brooklyn.entity.container.docker.DockerInfrastructure;

/**
 * Placement strategy for Docker containers.
 */
public abstract class AbstractDockerPlacementStrategy extends BasicConfigurableObject implements DockerAwarePlacementStrategy {

    @Override
    public DockerInfrastructure getDockerInfrastructure() { return config().get(DOCKER_INFRASTRUCTURE); }

    @Override
    public String toString() {
        return String.format("DockerAwarePlacementStrategy(%s@%s)", getClass().getSimpleName(), getId());
    }

}
