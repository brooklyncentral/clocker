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
package brooklyn.entity.container.docker.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.VanillaSoftwareProcessSshDriver;
import brooklyn.entity.container.docker.DockerContainer;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.docker.DockerContainerLocation;

/**
 * The SSH implementation of the {@link VanillaDockerApplicationDriver}.
 */
public class VanillaDockerApplicationSshDriver extends VanillaSoftwareProcessSshDriver implements VanillaDockerApplicationDriver {

    private static final Logger LOG = LoggerFactory.getLogger(VanillaDockerApplication.class);

    public VanillaDockerApplicationSshDriver(VanillaDockerApplicationImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    public DockerHost getDockerHost() {
        return getDockerContainer().getDockerHost();
    }

    public DockerContainer getDockerContainer() {
        return ((DockerContainerLocation) getMachine()).getOwner();
    }

}
