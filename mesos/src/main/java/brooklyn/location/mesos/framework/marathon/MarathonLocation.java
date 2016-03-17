/*
 * Copyright 2014-2016 by Cloudsoft Corporation Limited
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
package brooklyn.location.mesos.framework.marathon;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.base.Optional;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationDefinition;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.api.location.NoMachinesAvailableException;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampConstants;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.location.BasicLocationDefinition;
import org.apache.brooklyn.core.location.LocationConfigKeys;
import org.apache.brooklyn.core.location.dynamic.DynamicLocation;
import org.apache.brooklyn.core.location.dynamic.LocationOwner;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.exceptions.Exceptions;

import brooklyn.entity.container.DockerAttributes;
import brooklyn.entity.container.DockerUtils;
import brooklyn.entity.container.docker.DockerContainer;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.entity.mesos.framework.MesosFramework;
import brooklyn.entity.mesos.framework.marathon.MarathonFramework;
import brooklyn.entity.mesos.task.MesosTask;
import brooklyn.entity.mesos.task.marathon.MarathonTask;
import brooklyn.location.docker.DockerHostLocation;
import brooklyn.location.docker.DockerResolver;
import brooklyn.location.docker.strategy.DockerAwarePlacementStrategy;
import brooklyn.location.mesos.framework.MesosFrameworkLocation;

public class MarathonLocation extends MesosFrameworkLocation implements MachineProvisioningLocation<MarathonTaskLocation>,
        DynamicLocation<MarathonFramework, MarathonLocation> {

    private static final Logger LOG = LoggerFactory.getLogger(MarathonLocation.class);

    public static final String TASK_MUTEX = "task";

    public static final ConfigKey<String> LOCATION_NAME = ConfigKeys.newStringConfigKey("locationName");

    @SetFromFlag("locationRegistrationId")
    private String locationRegistrationId;

    private transient ReadWriteLock lock = new ReentrantReadWriteLock();

    public MarathonLocation() {
        this(Maps.newLinkedHashMap());
    }

    public MarathonLocation(Map properties) {
        super(properties);

        if (isLegacyConstruction()) {
            init();
        }
    }

    @Override
    public void rebind() {
        super.rebind();
        
        if (getConfig(LOCATION_NAME) != null) {
            register();
        }
    }

    @Override
    public LocationDefinition register() {
        String locationName = checkNotNull(getConfig(LOCATION_NAME), "config %s", LOCATION_NAME.getName());

        LocationDefinition check = getManagementContext().getLocationRegistry().getDefinedLocationByName(locationName);
        if (check != null) {
            throw new IllegalStateException("Location " + locationName + " is already defined: " + check);
        }

        String locationSpec = String.format(MarathonResolver.MARATHON_FRAMEWORK_SPEC, getId()) + String.format(":(name=\"%s\")", locationName);

        LocationDefinition definition = new BasicLocationDefinition(locationName, locationSpec, ImmutableMap.<String, Object>of());
        getManagementContext().getLocationRegistry().updateDefinedLocation(definition);
        
        locationRegistrationId = definition.getId();
        requestPersist();
        
        return definition;
    }
    
    @Override
    public void deregister() {
        if (locationRegistrationId != null) {
            getManagementContext().getLocationRegistry().removeDefinedLocation(locationRegistrationId);
            locationRegistrationId = null;
            requestPersist();
        }
    }
    
    public MarathonTaskLocation obtain() throws NoMachinesAvailableException {
        return obtain(Maps.<String,Object>newLinkedHashMap());
    }

    // Lookup task details from entity
    private Map<Object, Object> getTaskFlags(Entity entity) {
        Map<Object, Object> flags = MutableMap.of();

        // If we already have Docker image configured, use it
        Optional<String> imageName = Optional.fromNullable(entity.config().get(DockerContainer.DOCKER_IMAGE_NAME));
        if (imageName.isPresent()) {
            String imageVersion = Optional.fromNullable(entity.config().get(DockerContainer.DOCKER_IMAGE_TAG)).or("latest");
            flags.put(MarathonTask.DOCKER_IMAGE_NAME, imageName.get());
            flags.put(MarathonTask.DOCKER_IMAGE_TAG, imageVersion);

            // Docker command and args
            String command = entity.config().get(MarathonTask.COMMAND);
            if (command != null) flags.put(MarathonTask.COMMAND, command);
            List<String> args = entity.config().get(MarathonTask.ARGS);
            flags.put(MarathonTask.ARGS, args);

            // No SSH used for vanilla Docker images on Marathon
            flags.put(DockerAttributes.DOCKER_USE_SSH, Boolean.FALSE);
        } else {
            flags.put(DockerAttributes.DOCKER_USE_SSH, Boolean.TRUE);
        }

        return flags;
    }

    @Override
    public MarathonTaskLocation obtain(Map<?,?> flags) throws NoMachinesAvailableException {
        lock.readLock().lock();
        try {
            // Lookup entity from context or flags
            Object context = flags.get(LocationConfigKeys.CALLER_CONTEXT.getName());
            if (context == null || !(context instanceof Entity)) {
                throw new IllegalStateException("Invalid location context: " + context);
            }
            Entity entity = (Entity) context;

            // Check if entity type is supported
            if (!isSupported(entity)) {
                LOG.warn("Tried to start unsupported entity in Marathon location: {}", entity);
                throw new NoMachinesAvailableException("Unsupported entity type");
            }

            // Start a new task with flags from entity
            String name = getTaskName(entity);
            Map<Object, Object> taskFlags = MutableMap.builder()
                    .putAll(flags)
                    .put("entity", entity)
                    .put(MesosTask.TASK_NAME, name)
                    .put(MesosTask.MESOS_CLUSTER, getOwner().getMesosCluster())
                    .put(MesosTask.FRAMEWORK, getOwner())
                    .put(MesosTask.MANAGED, Boolean.TRUE)
                    .putAll(getTaskFlags(entity))
                    .build();
            LOG.info("Starting task {} on framework {}", name, framework);
            Group tasks = framework.sensors().get(MesosFramework.FRAMEWORK_TASKS);
            EntitySpec<?> spec = EntitySpec.create(getOwner().config().get(MarathonFramework.MARATHON_TASK_SPEC));
            spec.configure(taskFlags);
            Entity added = tasks.addMemberChild(spec);
            if (added == null) {
                throw new NoMachinesAvailableException("Failed to create Marathon task");
            } else {
                try {
                    Entities.invokeEffector(entity, added, Startable.START,  MutableMap.of("locations", ImmutableList.of(this))).getUnchecked();
                } catch (Exception e) {
                    ServiceStateLogic.setExpectedState(added, Lifecycle.ON_FIRE);
                    throw new NoMachinesAvailableException("Failed to start Marathon task", e);
                }
            }

            // Return the new task location
            MarathonTask marathonTask = (MarathonTask) added;
            return marathonTask.getDynamicLocation();
        } finally {
            lock.readLock().unlock();
        }
    }

    private String getTaskName(Entity entity) {
        String planId = entity.config().get(BrooklynCampConstants.PLAN_ID);
        String name = null;
        if (planId != null) {
            //Plan IDs are not unique even in a single application
            name = planId + "/" + entity.getId();
        } else {
            name = entity.getId();
        }
        return name.toLowerCase();
    }

    @Override
    public void release(MarathonTaskLocation location) {
        lock.readLock().lock();
        try {
            LOG.info("Releasing {}", location);

            Group cluster = framework.sensors().get(MesosFramework.FRAMEWORK_TASKS);
            MarathonTask task = location.getOwner();
            if (cluster.removeMember(task)) {
                LOG.info("Marathon framework {}: member {} released", framework, location);
            } else {
                LOG.warn("Marathon framework {}: member {} not found for release", framework, location);
            }

            // Now close and unmange the container
            try {
                location.close();
                task.stop();
            } catch (Exception e) {
                LOG.warn("Error stopping task: " + task, e);
                Exceptions.propagateIfFatal(e);
            } finally {
                Entities.unmanage(task);
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Map<String, Object> getProvisioningFlags(Collection<String> tags) {
        return MutableMap.of();
    }

    @Override
    public MarathonFramework getOwner() {
        return (MarathonFramework) framework;
    }

    @Override
    public MachineProvisioningLocation<MarathonTaskLocation> newSubLocation(Map<?, ?> newFlags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws IOException {
        LOG.info("Close called on Marathon framework {}: {}", framework, this);
        try {
            framework.stop(); // XXX check this
        } catch (Exception e) {
            LOG.info("{}: Closing Marathon framework: {}", e.getMessage(), this);
            throw Exceptions.propagate(e);
        } finally {
            LOG.info("Marathon framework closed: {}", this);
        }
    }

    public Lock getLock() {
        return lock.writeLock();
    }

    @Override
    public ToStringHelper string() {
        return super.string().add("framework", framework);
    }

}
