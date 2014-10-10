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
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.render.RendererHints;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.basic.BasicStartableImpl;
import brooklyn.entity.basic.DelegateEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.docker.DockerHostLocation;
import brooklyn.location.docker.DockerLocation;
import brooklyn.policy.PolicySpec;
import brooklyn.util.net.Cidr;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class WeaveInfrastructureImpl extends BasicStartableImpl implements WeaveInfrastructure {

    private static final Logger LOG = LoggerFactory.getLogger(WeaveInfrastructureImpl.class);

    static {
        RendererHints.register(WEAVE_SERVICES, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(DOCKER_INFRASTRUCTURE, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
    }

    private AbstractMembershipTrackingPolicy hostTrackerPolicy;
    private BasicGroup services;
    private Object mutex = new Object[0];
    private EntitySpec<WeaveContainer> weaveSpec;

    @Override
    public void init() {
        LOG.info("Starting Weave infrastructure id {}", getId());
        super.init();
        ConfigToAttributes.apply(this, DOCKER_INFRASTRUCTURE);

        weaveSpec = EntitySpec.create(WeaveContainer.class)
                .configure(SoftwareProcess.SUGGESTED_VERSION, getConfig(WEAVE_VERSION))
                .configure(SoftwareProcess.DOWNLOAD_URL, getConfig(WEAVE_DOWNLOAD_URL))
                .configure(WeaveContainer.WEAVE_CIDR, getConfig(WEAVE_CIDR))
                .configure(WeaveContainer.WEAVE_PORT, getConfig(WEAVE_PORT))
                .configure(WeaveContainer.WEAVE_INFRASTRUCTURE, this);

        services = addChild(EntitySpec.create(BasicGroup.class)
                .displayName("Weave Services"));

        if (Entities.isManaged(this)) {
            Entities.manage(services);
        }

        setAttribute(WEAVE_SERVICES, services);
        setAttribute(ALLOCATED_IPS, 0);
    }

    @Override
    public synchronized InetAddress get() {
        Cidr cidr = getConfig(WEAVE_CIDR);
        Integer allocated = getAttribute(ALLOCATED_IPS);
        InetAddress next = cidr.addressAtOffset(allocated + 1);
        setAttribute(ALLOCATED_IPS, allocated + 1);
        return next;
    }

    @Override
    public DynamicCluster getDockerHostCluster() {
        return getConfig(DOCKER_INFRASTRUCTURE).getDockerHostCluster();
    }

    @Override
    public Group getWeaveServices() { return services; }

    public static class MemberTrackingPolicy extends AbstractMembershipTrackingPolicy {
        @Override protected void onEntityEvent(EventType type, Entity member) {
            ((WeaveInfrastructureImpl) super.entity).onHostChanged(member);
        }
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        setAttribute(SERVICE_UP, Boolean.FALSE);

        addHostTrackerPolicy();
        super.start(locations);

        setAttribute(SERVICE_UP, Boolean.TRUE);
    }

    /**
     * De-register our {@link DockerLocation} and its children.
     */
    @Override
    public void stop() {
        setAttribute(SERVICE_UP, Boolean.FALSE);

        removeHostTrackerPolicy();
        super.stop();
    }

    protected void addHostTrackerPolicy() {
        Group hosts = getDockerHostCluster();
        if (hosts != null) {
            hostTrackerPolicy = addPolicy(PolicySpec.create(MemberTrackingPolicy.class)
                    .displayName("Docker host tracker")
                    .configure("group", hosts));
            LOG.info("Added policy {} to {}, during start", hostTrackerPolicy, this);
        }
    }
    
    protected void removeHostTrackerPolicy() {
        if (hostTrackerPolicy != null) {
            removePolicy(hostTrackerPolicy);
        }
    }

    protected void onHostAdded(Entity item) {
        synchronized (mutex) {
            SshMachineLocation machine = ((DockerHost) item).getDynamicLocation().getMachine();
            EntitySpec<WeaveContainer> spec = EntitySpec.create(weaveSpec)
                    .configure(WeaveContainer.DOCKER_HOST, (DockerHost) item);
            WeaveContainer weave = getWeaveServices().addChild(spec);
            Entities.manage(weave);
            getWeaveServices().addMember(weave);
            weave.start(ImmutableList.of(machine));
            if (LOG.isDebugEnabled()) LOG.debug("{} added weave service {}", this, weave);
        }
    }

    protected void onHostRemoved(Entity item) {
        synchronized (mutex) {
            SshMachineLocation machine = ((DockerHostLocation) item).getMachine();
            Optional<Entity> service = Iterables.tryFind(services.getMembers(), EntityPredicates.withLocation(machine));
            if (service.isPresent()) {
                WeaveContainer weave = (WeaveContainer) service.get();
                weave.stop();
                services.removeChild(weave);
                if (LOG.isDebugEnabled()) LOG.debug("{} removed weave service {}", this, weave);
            }
        }
    }

    protected void onHostChanged(Entity item) {
        synchronized (mutex) {
            boolean exists = getDockerHostCluster().hasMember(item);
            Boolean running = item.getAttribute(SERVICE_UP);
            if (exists && running) {
                onHostAdded(item);
            } else if (!exists) {
                onHostRemoved(item);
            }
        }
    }

}
