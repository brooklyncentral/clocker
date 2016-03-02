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
package clocker.docker.entity.container.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.location.dynamic.DynamicLocation;
import org.apache.brooklyn.entity.software.base.AbstractSoftwareProcessSshDriver;
import org.apache.brooklyn.location.ssh.SshMachineLocation;

/**
 * The SSH implementation of the {@link VanillaDockerApplicationDriver}.
 */
public class VanillaDockerApplicationSshDriver extends AbstractSoftwareProcessSshDriver implements VanillaDockerApplicationDriver {

    private static final Logger LOG = LoggerFactory.getLogger(VanillaDockerApplicationSshDriver.class);

    public VanillaDockerApplicationSshDriver(VanillaDockerApplicationImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    public Entity getOwnerEntity() {
        return (Entity) ((DynamicLocation) getMachine()).getOwner();
    }

    @Override
    public boolean isRunning() {
        return getOwnerEntity().sensors().get(Startable.SERVICE_UP);
    }

    @Override
    public void stop() {
        Entities.invokeEffector(getEntity(), getOwnerEntity(), Startable.STOP);
    }

    @Override
    public void install() {
        LOG.info("Container installed on {}", getOwnerEntity());
    }

    @Override
    public void customize() { }

    @Override
    public void launch() { }

}
