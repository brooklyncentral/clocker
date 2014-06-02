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
package brooklyn.location.docker;

import java.util.List;

import brooklyn.entity.Entity;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.location.Location;
import brooklyn.location.cloud.AbstractAvailabilityZoneExtension;
import brooklyn.management.ManagementContext;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class DockerHostExtension extends AbstractAvailabilityZoneExtension {

    private final DockerVirtualLocation location;

    public DockerHostExtension(ManagementContext managementContext, DockerVirtualLocation location) {
        super(managementContext);
        this.location = Preconditions.checkNotNull(location, "location");
    }

    @Override
    protected List<Location> doGetAllSubLocations() {
        List<Optional<Location>> result = Lists.newArrayList();
        for (Entity entity : location.getDockerHostList()) {
            DockerHost host = (DockerHost) entity;
            DockerHostLocation machine = host.getDynamicLocation();
            result.add(Optional.<Location>fromNullable(machine));
        }
        return ImmutableList.copyOf(Optional.presentInstances(result));
    }

    /** Forces call to {@link #doGetAllSubLocations()} each time. */
    @Override
    public List<Location> getAllSubLocations() {
        subLocations.set(null);
        return super.getAllSubLocations();
    }

    @Override
    protected boolean isNameMatch(Location loc, Predicate<? super String> namePredicate) {
        return namePredicate.apply(((DockerHostLocation) loc).getDockerInfrastructure().getId());
    }

}
