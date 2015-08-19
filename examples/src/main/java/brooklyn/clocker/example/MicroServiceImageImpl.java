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

import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.entity.container.docker.application.VanillaDockerApplication;
import brooklyn.entity.trait.Startable;
import brooklyn.location.docker.DockerLocation;

public class MicroServiceImageImpl extends AbstractApplication implements MicroServiceImage {

    private static final String MICRO_SERVICE_LOCATION = "my-docker-cloud";
    DockerInfrastructure dockerInfrastructure = null;
    VanillaDockerApplication vanillaDockerApplication = null;

    @Override
    public void initApp() {
        if (getManagementContext().getLocationRegistry().getDefinedLocationByName(MICRO_SERVICE_LOCATION) == null) {
            dockerInfrastructure = addChild(EntitySpec.create(DockerInfrastructure.class)
                    .configure(DockerInfrastructure.DOCKER_HOST_CLUSTER_MIN_SIZE, 1)
                    .configure(DockerInfrastructure.SDN_ENABLE, false)
                    .configure(DockerInfrastructure.LOCATION_NAME, MICRO_SERVICE_LOCATION)
                    .displayName("Docker Infrastructure"));
        }
        vanillaDockerApplication = addChild(EntitySpec.create(VanillaDockerApplication.class)
                .configure("containerName", config().get(CONTAINER_NAME))
                .configure("imageName", config().get(IMAGE_NAME))
                .configure("imageTag", config().get(IMAGE_TAG))
                .configure("openPorts", config().get(OPEN_PORTS))
                .configure("directPorts", config().get(DIRECT_PORTS)));
    }

    @Override
    protected void doStart(Collection<? extends Location> locations) {

        if (dockerInfrastructure != null) {
            Entities.invokeEffector(this, dockerInfrastructure, Startable.START, MutableMap.of("locations", getLocations())).getUnchecked();
        }

        DockerLocation dockerLocation = (DockerLocation) getManagementContext().getLocationRegistry().resolve(MICRO_SERVICE_LOCATION);
        vanillaDockerApplication.start(ImmutableList.of(dockerLocation));
    }

}
