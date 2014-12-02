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
package brooklyn.entity.container.weave;

import java.net.InetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.render.RendererHints;
import brooklyn.entity.basic.DelegateEntity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.event.feed.ConfigToAttributes;

/**
 * A single Docker container.
 */
public class WeaveContainerImpl extends SoftwareProcessImpl implements WeaveContainer {

    private static final Logger LOG = LoggerFactory.getLogger(WeaveContainer.class);

    @Override
    public void init() {
        super.init();
        ConfigToAttributes.apply(this, DOCKER_HOST);
        ConfigToAttributes.apply(this, WEAVE_INFRASTRUCTURE);
    }

    @Override
    public Class getDriverInterface() {
        return WeaveContainerDriver.class;
    }

    @Override
    public WeaveContainerDriver getDriver() {
        return (WeaveContainerDriver) super.getDriver();
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        super.connectServiceUpIsRunning();
    }

    @Override
    public void disconnectSensors() {
        super.disconnectServiceUpIsRunning();
        super.disconnectSensors();
    }

    @Override
    public DockerHost getDockerHost() {
        return getAttribute(DOCKER_HOST);
    }

    @Override
    public void preStart() {
        InetAddress address = getConfig(WeaveContainer.WEAVE_INFRASTRUCTURE).get();
        setAttribute(WEAVE_ADDRESS, address);
    }

    @Override
    public void postStart() {
        ((EntityInternal) getDockerHost()).setAttribute(WEAVE_CONTAINER, this);
    }

    @Override
    public InetAddress attachNetwork(String containerId) {
        InetAddress address = getDriver().attachNetwork(containerId);
        LOG.info("Attached {} to container ID {}", address.getHostAddress(), containerId);
        return address;
    }

    static {
        RendererHints.register(DOCKER_HOST, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(WEAVE_INFRASTRUCTURE, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(WEAVE_CONTAINER, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
    }

}
