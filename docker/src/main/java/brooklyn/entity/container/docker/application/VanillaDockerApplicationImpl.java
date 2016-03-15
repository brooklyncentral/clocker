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
package brooklyn.entity.container.docker.application;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.entity.software.base.SoftwareProcessImpl;

import brooklyn.entity.container.DockerAttributes;
import brooklyn.entity.container.DockerUtils;
import brooklyn.entity.container.docker.DockerContainer;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.location.docker.DockerContainerLocation;

public class VanillaDockerApplicationImpl extends SoftwareProcessImpl implements VanillaDockerApplication {

    private static final Logger LOG = LoggerFactory.getLogger(VanillaDockerApplicationImpl.class);

    @Override
    public String getIconUrl() { return "classpath://container.png"; }

    public String getDisplayName() {
        return String.format("Docker Container (%s)",
                Objects.firstNonNull(config().get(CONTAINER_NAME), config().get(IMAGE_NAME)));
    }

    @Override
    protected void connectSensors() {
        connectServiceUpIsRunning();
        super.connectSensors();
    }

    @Override
    public void preStart() {
        super.preStart();

        // Refresh the sensors that will be mapped, since we now have a location for the enricher to use
        for (AttributeSensor sensor : Iterables.filter(getEntityType().getSensors(), AttributeSensor.class)) {
            if (((DockerUtils.URL_SENSOR_NAMES.contains(sensor.getName()) ||
                            sensor.getName().endsWith(".url") ||
                            URI.class.isAssignableFrom(sensor.getType())) &&
                        !DockerUtils.BLACKLIST_URL_SENSOR_NAMES.contains(sensor.getName())) ||
                    (sensor.getName().matches("docker\\.port\\.[0-9]+") ||
                        PortAttributeSensorAndConfigKey.class.isAssignableFrom(sensor.getClass()))) {
                Object current = sensors().get(sensor);
                sensors().set(sensor, current);
            }
        }
    }

    @Override
    protected void disconnectSensors() {
        super.disconnectSensors();
        disconnectServiceUpIsRunning();
    }

    @Override
    public void rebind() {
        super.rebind();

        disconnectSensors();
        connectSensors();
    }

    @Override
    public Class<? extends VanillaDockerApplicationDriver> getDriverInterface() {
        return VanillaDockerApplicationDriver.class;
    }

    @Override
    public DockerContainer getDockerContainer() {
        DockerContainerLocation location = (DockerContainerLocation) Iterables.find(getLocations(), Predicates.instanceOf(DockerContainerLocation.class));
        return location.getOwner();
    }

    @Override
    public DockerHost getDockerHost() {
        return getDockerContainer().getDockerHost();
    }

    @Override
    public String getDockerfile() {
        return config().get(DOCKERFILE_URL);
    }

    @Override
    public List<Integer> getContainerPorts() {
        return config().get(DockerAttributes.DOCKER_OPEN_PORTS);
    }

}
