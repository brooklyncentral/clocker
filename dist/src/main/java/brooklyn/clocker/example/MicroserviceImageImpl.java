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
package brooklyn.clocker.example;

import java.util.Collection;

import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.entity.AbstractApplication;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.util.collections.MutableMap;

import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.entity.container.docker.application.VanillaDockerApplication;
import brooklyn.location.docker.DockerLocation;

public class MicroserviceImageImpl extends AbstractApplication implements MicroserviceImage {

    protected VanillaDockerApplication vanillaDockerApplication = null;

    @Override
    public void initApp() {
        vanillaDockerApplication = addChild(EntitySpec.create(VanillaDockerApplication.class)
                .configure("containerName", config().get(CONTAINER_NAME))
                .configure("imageName", config().get(IMAGE_NAME))
                .configure("imageTag", config().get(IMAGE_TAG))
                .configure("openPorts", config().get(OPEN_PORTS))
                .configure("directPorts", config().get(DIRECT_PORTS)));
    }

    @Override
    protected void doStart(Collection<? extends Location> locations) {
        Optional<Location> dockerLocation = Iterables.tryFind(getLocations(), Predicates.instanceOf(DockerLocation.class));

        if (!dockerLocation.isPresent()) {
            String locationName = DOCKER_LOCATION_PREFIX + getId();
            DockerInfrastructure dockerInfrastructure = addChild(EntitySpec.create(DockerInfrastructure.class)
                    .configure(DockerInfrastructure.DOCKER_HOST_CLUSTER_MIN_SIZE, 1)
                    .configure(DockerInfrastructure.SDN_ENABLE, false)
                    .configure(DockerInfrastructure.LOCATION_NAME, locationName)
                    .displayName("Docker Infrastructure"));

            Entities.start(dockerInfrastructure, getLocations());

            dockerLocation = Optional.of(getManagementContext().getLocationRegistry().resolve(locationName));
        }

        Entities.start(vanillaDockerApplication, dockerLocation.asSet());
    }

}
