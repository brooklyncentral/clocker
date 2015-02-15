package brooklyn.entity.container.docker;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.ServiceStateLogic;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.docker.DockerLocation;
import brooklyn.test.entity.TestEntity;
import brooklyn.test.entity.TestEntityImpl;

import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

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
