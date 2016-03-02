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
package clocker.mesos.entity.task;

import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clocker.mesos.entity.MesosCluster;
import clocker.mesos.entity.framework.MesosFramework;

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.core.feed.ConfigToAttributes;
import org.apache.brooklyn.core.location.Locations;
import org.apache.brooklyn.entity.stock.BasicStartableImpl;
import org.apache.brooklyn.entity.stock.DelegateEntity;
import org.apache.brooklyn.util.collections.MutableList;

/**
 * A single Mesos task.
 */
public class MesosTaskImpl extends BasicStartableImpl implements MesosTask {

    private static final Logger LOG = LoggerFactory.getLogger(MesosTask.class);

    @Override
    public void init() {
        super.init();

        ConfigToAttributes.apply(this, FRAMEWORK);
        ConfigToAttributes.apply(this, MESOS_CLUSTER);
        ConfigToAttributes.apply(this, MANAGED);

        if (!sensors().get(MANAGED)) {
            ConfigToAttributes.apply(this, TASK_NAME);
        }
    }

    @Override
    public String getShortName() {
        return sensors().get(TASK_NAME);
    }

    @Override
    public MesosCluster getMesosCluster() {
        return (MesosCluster) sensors().get(MESOS_CLUSTER);
    }

    @Override
    public MesosFramework getFramework() {
        return (MesosFramework) sensors().get(FRAMEWORK);
    }

    @Override
    public void start(Collection<? extends Location> locs) {
        addLocations(locs);
        List<Location> locations = MutableList.copyOf(Locations.getLocationsCheckingAncestors(locs, this));

        sensors().set(SERVICE_UP, Boolean.FALSE);

        super.start(locations);

        connectSensors();
    }

    /** Override in framework task implementations */
    public void connectSensors() {
        sensors().set(SERVICE_UP, Boolean.TRUE);
    }

    /** Override in framework task implementations */
    public void disconnectSensors() {
        sensors().set(SERVICE_UP, Boolean.FALSE);
    }

    @Override
    public void stop() {
        disconnectSensors();
    }

    static {
        RendererHints.register(FRAMEWORK, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(MESOS_CLUSTER, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
    }

}
