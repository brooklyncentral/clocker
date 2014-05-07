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

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.container.docker.DockerContainer;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.dynamic.DynamicLocation;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Objects.ToStringHelper;

/**
 * A {@link Location} that wraps a Docker container.
 * <p>
 * The underlying container is presented as an {@link SshMachineLocation} obtained using the jclouds Docker driver.
 */
public class DockerContainerLocation extends SshMachineLocation implements DynamicLocation<DockerContainer, DockerContainerLocation> {

    private static final Logger LOG = LoggerFactory.getLogger(DockerContainerLocation.class);

    @SetFromFlag("machine")
    private SshMachineLocation machine;

    @SetFromFlag("owner")
    private DockerContainer dockerContainer;

    @SetFromFlag("entity")
    private Entity entity;

    @Override
    public void init() {
        super.init();
        setEntity(entity);
    }

    public void setEntity(Entity entity) {
        dockerContainer.setRunningEntity(entity);
    }

    public Entity getEntity() {
        return dockerContainer.getRunningEntity();
    }

    @Override
    public DockerContainer getOwner() {
        return dockerContainer;
    }

    @Override
    public void close() throws IOException {
        machine.close();
        LOG.info("Close called on Docker container location: {}", this);
    }

    @Override
    public ToStringHelper string() {
        return super.string()
                .add("machine", machine)
                .add("dockerContainer", dockerContainer);
    }

}
