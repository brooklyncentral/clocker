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
package brooklyn.entity.container.policy;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.api.event.SensorEvent;
import org.apache.brooklyn.api.event.SensorEventListener;
import org.apache.brooklyn.api.policy.EnricherSpec;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.basic.BasicStartableImpl;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.location.docker.DockerLocation;
import brooklyn.location.docker.strategy.MaxContainersPlacementStrategy;

public class ContainerHeadroomEnricherTest extends BrooklynAppUnitTestSupport {

    private final Map<String, Duration> assertMap = ImmutableMap.of("timeout", Duration.ONE_SECOND);
    
    private EntityInternal entity;
    private RecordingSensorEventListener listener;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        
        entity = (EntityInternal) app.createAndManageChild(EntitySpec.create(DockerInfrastructure.class)
                .impl(DockerInfrastructureSimulated.class)
                .configure(MaxContainersPlacementStrategy.DOCKER_CONTAINER_CLUSTER_MAX_SIZE, 8));

        listener = new RecordingSensorEventListener();
        app.subscribe(entity, ContainerHeadroomEnricher.DOCKER_CONTAINER_CLUSTER_HOT, listener);
        app.subscribe(entity, ContainerHeadroomEnricher.DOCKER_CONTAINER_CLUSTER_COLD, listener);
        app.subscribe(entity, ContainerHeadroomEnricher.DOCKER_CONTAINER_CLUSTER_OK, listener);
    }
    
    @Test
    public void testNoEventsWhenAllOk() throws Exception {
        entity.addEnricher(EnricherSpec.create(ContainerHeadroomEnricher.class)
                .configure(ContainerHeadroomEnricher.CONTAINER_HEADROOM, 4));

        entity.setAttribute(DockerInfrastructure.DOCKER_HOST_COUNT, 2);
        entity.setAttribute(DockerInfrastructure.DOCKER_CONTAINER_COUNT, 8);

        assertNoEventsContinually();
    }

    // Integration because takes over a second, and because time-sensitive:
    // If we initially get two events with the second arriving more than one 
    // second later then our subsequent assertion will fail.  
    @Test(groups="integration")
    public void testTooHotWhenHeadroomExceeded() throws Exception {
        entity.addEnricher(EnricherSpec.create(ContainerHeadroomEnricher.class)
                .configure(ContainerHeadroomEnricher.CONTAINER_HEADROOM, 4));
        
        // Too hot: headroom insufficient by one container
        // Note we can get either 1 or 2 events for this (if the hostcount event is
        // processed after containerCount attribute has been set, then we'll get a too-hot
        // for that as well; otherwise it will ignore the event). Hence we use
        // clearEventsContinually below.
        entity.setAttribute(DockerInfrastructure.DOCKER_HOST_COUNT, 2);
        entity.setAttribute(DockerInfrastructure.DOCKER_CONTAINER_COUNT, 13);
        
        assertTooHot(new CurrentStatus()
                .hostCount(2)
                .needed(13 - (16 - 4))
                .utilization(13d/16) //0.8125
                .lowThreshold((16d - (4 + 8)) / 16) // 0.25
                .highThreshold(12d/16)); // 0.75
        
        // Too hot - 28 containers would require 4 hosts (leaving headroom of 4)
        listener.clearEventsContinually();
        entity.setAttribute(DockerInfrastructure.DOCKER_CONTAINER_COUNT, 28);

        assertTooHot(new CurrentStatus()
                .hostCount(2)
                .needed(28 - (16 - 4)) // 16
                .utilization(28d/16) // 1.75
                .lowThreshold((16d - (4 + 8)) / 16) // 0.25
                .highThreshold(12d/16)); // 0.75
        
        // Make everything ok again
        listener.clearEvents();
        entity.setAttribute(DockerInfrastructure.DOCKER_CONTAINER_COUNT, 8);

        assertOk(new CurrentStatus()
                .hostCount(2)
                .needed(8 - (16 - 4)) // 16
                .utilization(8d/16) // 1.75
                .lowThreshold((16d - (4 + 8)) / 16) // 0.25
                .highThreshold(12d/16)); // 0.75
        
        // Expect not to get repeated "ok"
        listener.clearEvents();
        entity.setAttribute(DockerInfrastructure.DOCKER_CONTAINER_COUNT, 9);

        assertNoEventsContinually();
    }

    // Integration because takes over a second, and because time-sensitive:
    // See comment on testTooHotThenOk.
    @Test(groups="integration")
    public void testTooColdThenOk() throws Exception {
        entity.addEnricher(EnricherSpec.create(ContainerHeadroomEnricher.class)
                .configure(ContainerHeadroomEnricher.CONTAINER_HEADROOM, 4));
        
        // Too cold - only need one host rather than 10
        entity.setAttribute(DockerInfrastructure.DOCKER_HOST_COUNT, 10);
        entity.setAttribute(DockerInfrastructure.DOCKER_CONTAINER_COUNT, 1);
        
        assertTooCold(new CurrentStatus()
                .hostCount(10)
                .needed(1 - (80 - 4))
                .utilization(1d/80)
                .lowThreshold((80d - (4 + 8)) / 80)
                .highThreshold(76d/80));
        
        // Too hot - only need one host rather than 2
        listener.clearEventsContinually();
        entity.setAttribute(DockerInfrastructure.DOCKER_HOST_COUNT, 2);

        assertTooCold(new CurrentStatus()
                .hostCount(2)
                .needed(1 - (16 - 4))
                .utilization(1d/16) // 1.75
                .lowThreshold((16d - (4 + 8)) / 16) // 0.25
                .highThreshold(12d/16)); // 0.75
        
        // Make everything ok again
        listener.clearEvents();
        entity.setAttribute(DockerInfrastructure.DOCKER_CONTAINER_COUNT, 8);

        assertOk(new CurrentStatus()
                .hostCount(2)
                .needed(8 - 16 + 4) // 16
                .utilization(8d/16) // 1.75
                .lowThreshold((16d - (4 + 8)) / 16) // 0.25
                .highThreshold(12d/16)); // 0.75
        
        // Expect not to get repeated "ok"
        listener.clearEvents();
        entity.setAttribute(DockerInfrastructure.DOCKER_CONTAINER_COUNT, 9);

        assertNoEventsContinually();
    }

    private void assertNoEventsContinually() {
        Asserts.succeedsContinually(new Runnable() {
            public void run() {
                assertEquals(listener.getEvents(), ImmutableList.of());
            }});
    }

    private void assertTooHot(final CurrentStatus status) {
        assertTemperatureEvent(status, ContainerHeadroomEnricher.DOCKER_CONTAINER_CLUSTER_HOT);
    }

    private void assertTooCold(final CurrentStatus status) {
        assertTemperatureEvent(status, ContainerHeadroomEnricher.DOCKER_CONTAINER_CLUSTER_COLD);
    }

    private void assertOk(final CurrentStatus status) {
        assertTemperatureEvent(status, ContainerHeadroomEnricher.DOCKER_CONTAINER_CLUSTER_OK);
    }

    private void assertTemperatureEvent(final CurrentStatus status, final BasicNotificationSensor<Map> eventType) {
        EntityTestUtils.assertAttributeEqualsEventually(assertMap, entity, ContainerHeadroomEnricher.CONTAINERS_NEEDED, status.needed);
        EntityTestUtils.assertAttributeEqualsEventually(assertMap, entity, ContainerHeadroomEnricher.DOCKER_CONTAINER_UTILISATION, status.utilization);

        Asserts.succeedsEventually(assertMap, new Runnable() {
            public void run() {
                List<SensorEvent<Object>> events = listener.getEvents();
                
                // Accept up to 2 duplicates - could be responding to rapid succession of setting hostCount + containerCount
                assertTrue(events.size() == 1 || events.size() == 2, "events="+events);
                if (events.size() == 2) {
                    assertEquals(events.get(0).getSensor(), events.get(1).getSensor());
                    assertEquals(events.get(0).getValue(), events.get(1).getValue());
                }
                assertEquals(events.get(0).getSensor(), eventType);
                assertEquals(events.get(0).getValue(), ImmutableMap.of(
                        "pool.current.size", status.hostCount, 
                        "pool.current.workrate", status.utilization,
                        "pool.low.threshold", status.lowThreshold,
                        "pool.high.threshold", status.highThreshold));
            }});
    }

    private static class CurrentStatus {
        int hostCount;
        int needed;
        double utilization;
        double lowThreshold;
        double highThreshold;
        
        CurrentStatus hostCount(int val) {
            hostCount = val; return this;
        }
        CurrentStatus needed(int val) {
            needed = val; return this;
        }
        CurrentStatus utilization(double val) {
            utilization = val; return this;
        }
        CurrentStatus lowThreshold(double val) {
            lowThreshold = val; return this;
        }
        CurrentStatus highThreshold(double val) {
            highThreshold = val; return this;
        }
    }
    
    public static class DockerInfrastructureSimulated extends BasicStartableImpl implements DockerInfrastructure {
        private int currentSize = 0;
        
        @Override
        public Integer resize(Integer desiredSize) {
            currentSize = desiredSize;
            return currentSize;
        }

        @Override
        public Integer getCurrentSize() {
            return currentSize;
        }

        @Override
        public boolean isLocationAvailable() {
            return false;
        }

        @Override
        public void deleteLocation() {
            // no-op
        }

        @Override
        public List<Entity> getDockerHostList() {
            return ImmutableList.<Entity>of();
        }

        @Override
        public List<Entity> getDockerContainerList() {
            return ImmutableList.<Entity>of();
        }

        @Override
        public DockerLocation getDynamicLocation() {
            throw new UnsupportedOperationException();
        }

        @Override
        public DockerLocation createLocation(Map<String, ?> flags) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DynamicCluster getDockerHostCluster() {
            throw new UnsupportedOperationException();
        }

        @Override
        public DynamicGroup getContainerFabric() {
            throw new UnsupportedOperationException();
        }
    }
    
    public static class RecordingSensorEventListener implements SensorEventListener<Object> {
        List<SensorEvent<Object>> events = Lists.newCopyOnWriteArrayList();
        
        @Override
        public void onEvent(SensorEvent<Object> event) {
            events.add(event);
        }
        
        public List<SensorEvent<Object>> getEvents() {
            return events;
        }
        
        public void clearEvents() {
            events.clear();
        }
        
        public void clearEventsContinually() {
            Time.sleep(Duration.ONE_SECOND);
            clearEvents();
        }
    }
}
