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
package brooklyn.entity.mesos.task.marathon;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.python.google.common.base.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampConstants;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.location.jclouds.JcloudsLocationConfig;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.exceptions.Exceptions;

import brooklyn.entity.mesos.MesosCluster;
import brooklyn.entity.mesos.framework.MesosFramework;
import brooklyn.entity.mesos.framework.marathon.MarathonFramework;
import brooklyn.entity.mesos.task.MesosTask;
import brooklyn.entity.mesos.task.MesosTaskImpl;

/**
 * A single Marathon task.
 */
public class MarathonTaskImpl extends MesosTaskImpl implements MarathonTask {

    private static final Logger LOG = LoggerFactory.getLogger(MesosTask.class);

    @Override
    public void init() {
        super.init();

        String name = Optional.fromNullable(config().get(BrooklynCampConstants.PLAN_ID))
                .or(Optional.fromNullable(config().get(MarathonTask.APPLICATION_ID)))
                .or(Optional.fromNullable(config().get(MesosTask.TASK_NAME)))
                .or(getId());
        sensors().set(MesosTask.TASK_NAME, name);
        LOG.info("Marathon task {}: {}", name, getId());
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        sensors().set(SERVICE_UP, Boolean.FALSE);

        super.start(locations);

        MesosCluster cluster = (MesosCluster) sensors().get(MESOS_CLUSTER);
        MarathonFramework marathon = null;
        for (Entity framework : cluster.sensors().get(MesosCluster.MESOS_FRAMEWORKS).getMembers()) {
            if (framework instanceof MarathonFramework) {
                marathon = (MarathonFramework) framework;
            }
        }
        if (marathon == null) throw new IllegalStateException("Cannot start without a Marathon framework");
        sensors().set(FRAMEWORK, marathon);
        marathon.sensors().get(MesosFramework.FRAMEWORK_TASKS).addMember(this);

        try {
            String name = sensors().get(MesosTask.TASK_NAME);
            Map<String, Object> flags = getMarathonFlags();
            LOG.debug("Starting task {} on {} with flags: {}",
                    new Object[] { name, marathon, Joiner.on(",").withKeyValueSeparator("=").join(flags) });
            marathon.startApplication(name, flags);
        } catch (Exception e) {
            Exceptions.propagate(e);
        }

        sensors().set(SERVICE_UP, Boolean.TRUE);
    }

    private Map<String, Object> getMarathonFlags() {
        Map<String, Object> flags = MutableMap.of();
        Map<String, Object> provisioningProperties = ImmutableMap.copyOf(config().get(SoftwareProcess.PROVISIONING_PROPERTIES));

        // CPU
        Double cpus;
        Integer minCores =  config().get(JcloudsLocationConfig.MIN_CORES);
        if (minCores == null) {
            minCores = (Integer) provisioningProperties.get(JcloudsLocationConfig.MIN_CORES.getName());
        }
        if (minCores == null) {
            cpus = config().get(CPU_RESOURCES);
        } else {
            cpus = 1.0d * minCores;
        }
        flags.put("cpus", cpus);

        // Memory
        Integer memory = (Integer) config().get(JcloudsLocationConfig.MIN_RAM);
        if (memory == null) {
            memory = (Integer) provisioningProperties.get(JcloudsLocationConfig.MIN_RAM.getName());
        }
        if (memory == null) {
            memory = config().get(MEMORY_RESOURCES);
        }
        flags.put("memory", memory);

        // Inbound ports
        List<Integer> openPorts = ImmutableList.copyOf(config().get(MARATHON_OPEN_PORTS));
        flags.put("openPorts", openPorts);

        // Environment variables
        Map<String, Object> environment = ImmutableMap.copyOf(config().get(MARATHON_ENVIRONMENT));
        flags.put("environment", environment);

        // Docker command and image
        flags.put("command", config().get(COMMAND));
        flags.put("imageName", config().get(DOCKER_IMAGE_NAME));
        flags.put("imageVersion", config().get(DOCKER_IMAGE_TAG));

        return flags;
    }

}
