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
package brooklyn.entity.container.docker;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.docker.DockerContainerLocation;
import brooklyn.location.docker.DockerHostLocation;
import brooklyn.location.dynamic.DynamicLocation;
import brooklyn.management.LocationManager;
import brooklyn.util.collections.MutableMap;

public class DockerContainerImpl extends SoftwareProcessImpl implements DockerContainer {

    private static final Logger log = LoggerFactory.getLogger(DockerContainerImpl.class);
    private static final AtomicInteger counter = new AtomicInteger(0);
    private DockerContainerLocation container;

    @Override
    public void init() {
        log.info("Starting Docker container id {}", getId());

        String dockerContainerName = String.format(getConfig(DockerContainer.DOCKER_CONTAINER_NAME_FORMAT), getId(),
                counter.incrementAndGet());
        setDisplayName(dockerContainerName);
        setAttribute(DOCKER_CONTAINER_NAME, dockerContainerName);
    }

    @Override
    public void doStart(Collection<? extends Location> locations) {
        super.doStart(locations);

        Map<String, ?> flags = MutableMap.<String, Object>builder()
                .putAll(getConfig(LOCATION_FLAGS))
                .build();
        container = createLocation(flags);
        log.info("New Docker container location {} created", container);
    }

    @Override
    public void doStop() {
        deleteLocation();

        super.doStop();
    }

    @Override
    public void deleteLocation() {
        LocationManager mgr = getManagementContext().getLocationManager();
        DockerContainerLocation location = getDynamicLocation();
        if (location != null && mgr.isManaged(location)) {
            mgr.unmanage(location);
            setAttribute(DYNAMIC_LOCATION,  null);
        }
    }

    @Override
    public String getDockerContainerName() {
        return getAttribute(DOCKER_CONTAINER_NAME);
    }

    @Override
    public DockerHost getDockerHost() {
        return getConfig(DOCKER_HOST);
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();
    }

    @Override
    public void disconnectSensors() {
        disconnectServiceUpIsRunning();
        super.disconnectSensors();
    }

    @Override
    public void shutDown() {
        String dockerContainerName = getAttribute(DockerContainer.DOCKER_CONTAINER_NAME);
        log.info("Shut-Down {}", dockerContainerName);
    }

    @Override
    public void pause() {
        String dockerContainerName = getAttribute(DockerContainer.DOCKER_CONTAINER_NAME);
        log.info("Pausing {}", dockerContainerName);
    }

    @Override
    public void resume() {
        String dockerContainerName = getAttribute(DockerContainer.DOCKER_CONTAINER_NAME);
        log.info("Resume {}", dockerContainerName);
    }

    @Override
    public Class getDriverInterface() {
        return DockerContainerDriver.class;
    }

    @Override
    public String getShortName() {
        return "Docker container";
    }

    @Override
    public DockerContainerLocation getDynamicLocation() {
        return (DockerContainerLocation) getAttribute(DYNAMIC_LOCATION);
    }

    @Override
    public DockerContainerLocation createLocation(Map<String, ?> flags) {
        DockerHost dockerHost = getConfig(DOCKER_HOST);
        DockerHostLocation host = dockerHost.getDynamicLocation();
        String locationName = host.getId() + "-" + getId();
        LocationSpec<DockerContainerLocation> spec = LocationSpec.create(DockerContainerLocation.class)
                .parent(host)
                .configure(flags)
                .configure(DynamicLocation.OWNER, this)
                .configure("host", host.getMachine()) // The underlying SshMachineLocation
                .configure("address", host.getAddress()) // FIXME
                .configure(host.getMachine().getAllConfig(true))
                .displayName(getDockerContainerName())
                .id(locationName);
        DockerContainerLocation location = getManagementContext().getLocationManager().createLocation(spec);
        setAttribute(DYNAMIC_LOCATION, location);
        setAttribute(LOCATION_NAME, location.getId());

        return location;
    }

    @Override
    public boolean isLocationAvailable() {
        // TODO implementation
        return container != null;
    }

}
