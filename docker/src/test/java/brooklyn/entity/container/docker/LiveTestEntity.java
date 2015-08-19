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
package brooklyn.entity.container.docker;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.api.location.NoMachinesAvailableException;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.core.test.entity.TestEntityImpl;
import org.apache.brooklyn.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.entity.lifecycle.ServiceStateLogic;

import brooklyn.location.docker.DockerLocation;

@ImplementedBy(LiveTestEntity.LiveTestEntityImpl.class)
public interface LiveTestEntity extends TestEntity {

    MachineProvisioningLocation getProvisioningLocation();
    MachineLocation getObtainedLocation();

    public static class LiveTestEntityImpl extends TestEntityImpl implements LiveTestEntity {

        private static final Logger LOG = LoggerFactory.getLogger(LiveTestEntity.class);
        private DockerLocation provisioningLocation;
        private MachineLocation obtainedLocation;

        @Override
        public void start(final Collection<? extends Location> locs) {
            LOG.trace("Starting {}", this);
            callHistory.add("start");
            ServiceStateLogic.setExpectedState(this, Lifecycle.STARTING);
            counter.incrementAndGet();
            addLocations(locs);
            provisioningLocation = (DockerLocation) Iterables.find(locs, Predicates.instanceOf(DockerLocation.class));
            try {
                obtainedLocation = provisioningLocation.obtain(provisioningLocation.getAllConfig(true));
            } catch (NoMachinesAvailableException e) {
                throw Throwables.propagate(e);
            }
            addLocations(ImmutableList.of(obtainedLocation));
            ServiceStateLogic.setExpectedState(this, Lifecycle.RUNNING);
        }

        @Override
        public void stop() {
            LOG.trace("Stopping {}", this);
            callHistory.add("stop");
            ServiceStateLogic.setExpectedState(this, Lifecycle.STOPPING);
            counter.decrementAndGet();
            if (provisioningLocation != null && obtainedLocation != null) {
                provisioningLocation.release(obtainedLocation);
            }
            ServiceStateLogic.setExpectedState(this, Lifecycle.STOPPED);
        }

        public MachineProvisioningLocation getProvisioningLocation() {
            return provisioningLocation;
        }

        public MachineLocation getObtainedLocation() {
            return obtainedLocation;
        }
    }

}
