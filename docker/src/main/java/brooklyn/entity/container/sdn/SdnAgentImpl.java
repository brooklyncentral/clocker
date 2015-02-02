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
package brooklyn.entity.container.sdn;

import java.net.InetAddress;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.render.RendererHints;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.DelegateEntity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.event.feed.ConfigToAttributes;

/**
 * An SDN agent process on a Docker host.
 */
public abstract class SdnAgentImpl extends SoftwareProcessImpl implements SdnAgent {

    private static final Logger LOG = LoggerFactory.getLogger(SdnAgent.class);

    protected transient Object addressMutex = new Object[0];

    @Override
    public void init() {
        super.init();

        ConfigToAttributes.apply(this, DOCKER_HOST);
        ConfigToAttributes.apply(this, SDN_PROVIDER);
    }

    @Override
    public SdnAgentDriver getDriver() {
        return (SdnAgentDriver) super.getDriver();
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
        synchronized (addressMutex) {
            InetAddress address = getAttribute(SDN_PROVIDER).getNextAddress();
            setAttribute(SDN_AGENT_ADDRESS, address);
            getAgentAddresses().put(getId(), address);
        }
    }

    @Override
    public void postStart() {
        ((EntityInternal) getDockerHost()).setAttribute(SDN_AGENT, this);
    }

    public Map<String, InetAddress> getAgentAddresses() {
        synchronized (addressMutex) {
            return getAttribute(SDN_PROVIDER).getAgentAddresses();
        }
    }

    public Map<String, InetAddress> getContainerAddresses() {
        synchronized (addressMutex) {
            return getAttribute(SDN_PROVIDER).getContainerAddresses();
        }
    }

    @Override
    public InetAddress attachNetwork(String containerId, Entity entity) {
        synchronized (addressMutex) {
            InetAddress address = getDriver().attachNetwork(containerId, entity);
            getContainerAddresses().put(containerId, address);
            LOG.info("Attached {} to container ID {}", address.getHostAddress(), containerId);
            return address;
        }
    }

    static {
        RendererHints.register(DOCKER_HOST, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(SDN_PROVIDER, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(SDN_AGENT, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
    }

}
