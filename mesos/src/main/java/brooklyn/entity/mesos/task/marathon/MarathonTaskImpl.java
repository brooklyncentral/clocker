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

import org.python.google.common.base.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampConstants;
import org.apache.brooklyn.util.exceptions.Exceptions;

import brooklyn.entity.mesos.MesosCluster;
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
        LOG.info("Starting Marathon task id {}", getId());
        super.init();
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

        String applicationId = Optional.fromNullable(config().get(BrooklynCampConstants.PLAN_ID)).or(Optional.fromNullable(config().get(MarathonTask.APPLICATION_ID))).or(getId());
        String command = config().get(COMMAND);
        String imageName = config().get(DOCKER_IMAGE_NAME);
        String imageVersion = config().get(DOCKER_IMAGE_TAG);
        try {
            marathon.startApplication(applicationId, command, imageName, imageVersion);
        } catch (Exception e) {
            Exceptions.propagate(e);
        }

        sensors().set(SERVICE_UP, Boolean.TRUE);
    }

}
