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
package brooklyn.entity.mesos.task;

import java.util.Collection;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.feed.ConfigToAttributes;
import org.apache.brooklyn.entity.stock.BasicStartableImpl;

import brooklyn.location.mesos.MesosLocation;

/**
 * A single Mesos task.
 */
public class MesosTaskImpl extends BasicStartableImpl implements MesosTask {

    private static final Logger LOG = LoggerFactory.getLogger(MesosTask.class);

    @Override
    public void init() {
        super.init();

        // These can be set as configuration values
        ConfigToAttributes.apply(this, TASK_NAME);
        ConfigToAttributes.apply(this, FRAMEWORK);
    }

    @Override
    public String getShortName() {
        return "brooklyn-" + sensors().get(TASK_NAME).toLowerCase(Locale.ENGLISH);
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        sensors().set(SERVICE_UP, Boolean.FALSE);

        super.start(locations);

        MesosLocation mesos = null;
        for (Location location : locations) {
            if (location instanceof MesosLocation) {
                mesos = (MesosLocation) location;
            }
        }
        if (mesos == null) throw new IllegalStateException("Cannot start in a non-Mesos location");
        LOG.info("task started with name {} and id {}: {}");

        sensors().set(MESOS_CLUSTER, mesos.getOwner());
        sensors().set(SERVICE_UP, Boolean.TRUE); // TODO generate this from task status
    }

    /**
     * Stop this task.
     */
    @Override
    public void stop() {
        // TODO call stop
        sensors().set(SERVICE_UP, Boolean.FALSE);
    }

}
