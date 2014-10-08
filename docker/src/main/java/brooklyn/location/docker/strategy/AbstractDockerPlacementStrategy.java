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
package brooklyn.location.docker.strategy;

import javax.annotation.Nullable;

import brooklyn.basic.BasicConfigurableObject;
import brooklyn.config.ConfigKey;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.entity.trait.Identifiable;
import brooklyn.location.docker.DockerVirtualLocation;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Function;

/**
 * Placement strategy for Docker containers.
 */
public abstract class AbstractDockerPlacementStrategy extends BasicConfigurableObject implements DockerAwarePlacementStrategy {

    public static final Function<Identifiable, String> identity() { return identity; }

    private static final Function<Identifiable, String> identity = new Function<Identifiable, String>() {
        @Override
        public String apply(@Nullable Identifiable input) {
            return input.getClass().getSimpleName() + ":" + input.getId();
        }
    };

    @SetFromFlag("infrastructure")
    public static final ConfigKey<DockerInfrastructure> DOCKER_INFRASTRUCTURE = DockerVirtualLocation.INFRASTRUCTURE;

    @Override
    public DockerInfrastructure getDockerInfrastructure() { return getConfig(DOCKER_INFRASTRUCTURE); }

    @Override
    public String toString() {
        return String.format("DockerAwarePlacementStrategy(%s)", getClass().getSimpleName());
    }

}
