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

import static java.lang.String.format;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.BasicStartableImpl;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.function.FunctionPollConfig;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.docker.DockerContainerLocation;
import brooklyn.location.docker.DockerHostLocation;
import brooklyn.location.dynamic.DynamicLocation;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsLocationConfig;
import brooklyn.location.jclouds.JcloudsSshMachineLocation;
import brooklyn.management.LocationManager;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.time.Duration;

/**
 * A single Docker container.
 */
public class DockerContainerImpl extends BasicStartableImpl implements DockerContainer {

    private static final Logger LOG = LoggerFactory.getLogger(DockerContainerImpl.class);
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private transient FunctionFeed sensorFeed;

    @Override
    public void init() {
        LOG.info("Starting Docker container id {}", getId());

        String dockerContainerName = format(getConfig(DockerContainer.DOCKER_CONTAINER_NAME_FORMAT), getId(), COUNTER.incrementAndGet());
        setDisplayName(dockerContainerName);
        setAttribute(DOCKER_CONTAINER_NAME, dockerContainerName);
        setRunningEntity(getConfig(ENTITY));
    }

    protected void connectSensors() {
        sensorFeed = FunctionFeed.builder()
                .entity(this)
                .period(Duration.TEN_SECONDS)
                .poll(new FunctionPollConfig<Boolean, Boolean>(SERVICE_UP)
                        .callable(new Callable<Boolean>() {
                                @Override
                                public Boolean call() throws Exception {
                                    return getDynamicLocation().isSshable();
                                }
                        }))
                .build();
    }

    public void disconnectSensors() {
        if (sensorFeed !=  null) sensorFeed.stop();
    }

    @Override
    public Entity getRunningEntity() {
        return getAttribute(ENTITY);
    }

    public void setRunningEntity(Entity entity) {
        setAttribute(ENTITY, entity);
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
    public String getShortName() {
        return "Docker container";
    }

    @Override
    public DockerContainerLocation getDynamicLocation() {
        return (DockerContainerLocation) getAttribute(DYNAMIC_LOCATION);
    }

    @Override
    public boolean isLocationAvailable() {
        return getDynamicLocation() != null;
    }

    @Override
    public void shutDown() {
        String dockerContainerName = getAttribute(DockerContainer.DOCKER_CONTAINER_NAME);
        LOG.info("Shut-Down {}", dockerContainerName);
    }

    @Override
    public void pause() {
        String dockerContainerName = getAttribute(DockerContainer.DOCKER_CONTAINER_NAME);
        LOG.info("Pausing {}", dockerContainerName);
    }

    @Override
    public void resume() {
        String dockerContainerName = getAttribute(DockerContainer.DOCKER_CONTAINER_NAME);
        LOG.info("Resume {}", dockerContainerName);
    }

    /**
     * Create a new {@link DockerContainerLocation} wrapping a machine from the host's {@link JcloudsLocation}.
     */
    @Override
    public DockerContainerLocation createLocation(Map flags) {
        DockerHost dockerHost = getDockerHost();
        DockerHostLocation host = dockerHost.getDynamicLocation();
        String locationName = host.getId() + "-" + getId();

        try {
            // Create a new container using jclouds Docker driver
            JcloudsSshMachineLocation container = host.getJcloudsLocation().obtain(flags);

            // Create our wrapper location around the container
            LocationSpec<DockerContainerLocation> spec = LocationSpec.create(DockerContainerLocation.class)
                    .parent(host)
                    .configure(flags)
                    .configure(DynamicLocation.OWNER, this)
                    .configure("machine", container) // the underlying JcloudsLocation
                    .configure(container.getAllConfig(true))
                    .configure("port", getAttribute(DockerHost.DOCKER_PORT))
                    .displayName(getDockerContainerName())
                    .id(locationName);
            DockerContainerLocation location = getManagementContext().getLocationManager().createLocation(spec);
            setAttribute(DYNAMIC_LOCATION, location);
            setAttribute(LOCATION_NAME, location.getId());

            LOG.info("New Docker container location {} created", location);
            return location;
        } catch (NoMachinesAvailableException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public void deleteLocation() {
        DockerContainerLocation location = getDynamicLocation();

        if (location != null) {
            LocationManager mgr = getManagementContext().getLocationManager();
            if (mgr.isManaged(location)) {
                mgr.unmanage(location);
            }
        }

        setAttribute(DYNAMIC_LOCATION, null);
        setAttribute(LOCATION_NAME, null);
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        setAttribute(SoftwareProcess.SERVICE_STATE, Lifecycle.STARTING);
        setAttribute(SoftwareProcess.SERVICE_UP, Boolean.FALSE);

        Map<String, ?> flags = MutableMap.<String, Object>builder()
                .putAll(getConfig(LOCATION_FLAGS))
                .put(JcloudsLocationConfig.IMAGE_ID.getName(), getConfig(DOCKER_IMAGE_ID))
                .put("loginUser", "root") // FIXME add ConfigKey for these two flags
                .put("loginUser.password", "password")
                .put("dontCreateUser", "true")
                .build();
        createLocation(flags);

        setAttribute(SSH_MACHINE_LOCATION, getDynamicLocation().getMachine());

        connectSensors();

        super.start(locations);

        setAttribute(SoftwareProcess.SERVICE_STATE, Lifecycle.RUNNING);
    }

    @Override
    public void stop() {
        setAttribute(SoftwareProcess.SERVICE_STATE, Lifecycle.STOPPING);

        super.stop();

        disconnectSensors();

        setAttribute(SSH_MACHINE_LOCATION, null);

        deleteLocation();

        setAttribute(SoftwareProcess.SERVICE_UP, Boolean.FALSE);
        setAttribute(SoftwareProcess.SERVICE_STATE, Lifecycle.STOPPED);
    }

}
