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

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

import org.apache.brooklyn.entity.software.base.SoftwareProcessImpl;

import brooklyn.entity.container.DockerAttributes;
import brooklyn.entity.container.docker.DockerContainer;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.location.docker.DockerContainerLocation;

public class VanillaDockerApplicationImpl extends SoftwareProcessImpl implements VanillaDockerApplication {

    private static final Logger LOG = LoggerFactory.getLogger(VanillaDockerApplicationImpl.class);

    @Override
    protected void connectSensors() {
        connectServiceUpIsRunning();
        super.connectSensors();
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
