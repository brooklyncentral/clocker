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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.location.byon.FixedListMachineProvisioningLocation;
import org.apache.brooklyn.util.collections.MutableMap;

import brooklyn.entity.container.DockerAttributes;
import brooklyn.entity.container.DockerUtils;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.entity.container.docker.application.VanillaDockerApplicationImpl;
import brooklyn.location.docker.DockerLocation;

public abstract class MicroserviceImpl extends VanillaDockerApplicationImpl implements Microservice {

    private static final Logger LOG = LoggerFactory.getLogger(Microservice.class);

    public static final String DOCKER_LOCATION_PREFIX = "docker-";

    /** Start a {@link DockerInfrastructure} with a single host. */
    @Override
    public void preStart() {
        super.preStart();
        String containerName = DockerUtils.getContainerName(this).or(getId());
        Optional<Location> dockerLocation = Iterables.tryFind(getLocations(), Predicates.instanceOf(DockerLocation.class));
        if (!dockerLocation.isPresent()) {
            LOG.info("Starting host for microservice ({}) {}: {}",
                    new Object[] { this, containerName, Iterables.toString(getLocations()) });

            // Synthesise a DockerLocation from the machine we just started in
            FixedListMachineProvisioningLocation<?> provisioner = getManagementContext().getLocationManager().createLocation(LocationSpec.create(FixedListMachineProvisioningLocation.class)
                    .displayName("Docker Machine")
                    .configure("machines", getLocations()));
            String locationName = DOCKER_LOCATION_PREFIX + containerName;
            DockerInfrastructure dockerInfrastructure = addChild(EntitySpec.create(DockerInfrastructure.class)
                    .configure(DockerInfrastructure.DOCKER_HOST_CLUSTER_MIN_SIZE, 1)
                    .configure(DockerInfrastructure.SDN_ENABLE, false)
                    .configure(DockerInfrastructure.LOCATION_NAME, locationName)
                    .displayName(String.format("Docker Infrastructure (%s)", containerName)));
            Entities.start(dockerInfrastructure, ImmutableList.of(provisioner));

            // Now start ourselves in this new location
            dockerLocation = Optional.fromNullable(getManagementContext().getLocationRegistry().resolve(locationName));
            clearLocations();
            addLocations(dockerLocation.asSet());
            Entities.start(this, dockerLocation.asSet());
        } else {
            LOG.info("Starting container for microservice ({}) {}: {}",
                    new Object[] { this, containerName, dockerLocation.get() });
            // No action required if we found a DockerLocation
        }
    }

    @Override
    public void postStop() {
        // Stop the DockerInfrastructure if it exists as a child
        Optional<Entity> dockerInfrastructure = Iterables.tryFind(getChildren(), Predicates.instanceOf(DockerInfrastructure.class));
        if (dockerInfrastructure.isPresent()) {
            Entities.invokeEffector(this, dockerInfrastructure.get(), Startable.STOP, MutableMap.<String, Object>of()).getUnchecked();
        }
        super.postStop();
    }

    @Override
    public void rebind() {
        // TODO do we need to do anything special here?
        super.rebind();
    }

    public static class MicroserviceImageImpl extends MicroserviceImpl implements MicroserviceImage {
        // Required for EntitySpec creation
    }

    public static class MicroserviceDockerfileImpl extends MicroserviceImpl implements MicroserviceDockerfile {
        // Required for EntitySpec creation
    }

}
