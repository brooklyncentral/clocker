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
package brooklyn.entity.container.docker;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.FixedListMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.docker.DockerContainerLocation;
import brooklyn.location.docker.DockerHostLocation;
import brooklyn.location.docker.DockerLocation;
import brooklyn.management.ManagementContext;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;

/**
 * Brooklyn managed basic Docker infrastructure.
 */
public class DockerHostLiveTest {

    public static final String USER = "andrea";
    public static final String ADDRESS = "37.58.96.163";
    private BrooklynLauncher launcher;
    private ManagementContext managementContext;
    private SshMachineLocation machine;
    private TestApplication app;
    private FixedListMachineProvisioningLocation<SshMachineLocation> machinePool;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = Entities.newManagementContext();
        launcher = BrooklynLauncher.newInstance()
                .managementContext(managementContext)
                .start();
        
        machine = managementContext.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("user", USER)
                .configure("address", ADDRESS));
        machinePool = managementContext.getLocationManager().createLocation(LocationSpec.create(FixedListMachineProvisioningLocation.class)
                .configure("machines", ImmutableList.of(machine)));
        
        app = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
        if (launcher != null) launcher.terminate();
    }

    @Test(groups="Integration")
    public void testRegistersLocations() throws Exception {
        DockerInfrastructure dockerInfrastructure = app.createAndManageChild(EntitySpec.create(DockerInfrastructure.class)
                .configure(DockerInfrastructure.DOCKER_HOST_CLUSTER_MIN_SIZE, 1)
                .configure(DockerInfrastructure.LOCATION_NAME_PREFIX, "dynamicdockertest")
                .displayName("Docker Infrastructure"));
        dockerInfrastructure.start(ImmutableList.of(machinePool));

        LocationDefinition infraLocDef = findLocationMatchingName("dynamicdockertest.*");
        Location infraLoc = managementContext.getLocationRegistry().resolve(infraLocDef);
        assertTrue(infraLoc instanceof DockerLocation, "loc="+infraLoc);
    }

    @Test(groups="Integration")
    public void testObtainContainerFromInfrastructure() throws Exception {
        DockerInfrastructure dockerInfrastructure = app.createAndManageChild(EntitySpec.create(DockerInfrastructure.class)
                .configure(DockerInfrastructure.DOCKER_HOST_CLUSTER_MIN_SIZE, 1));
        dockerInfrastructure.start(ImmutableList.of(machinePool));

        DockerHost dockerHost = (DockerHost) Iterables.getOnlyElement(dockerInfrastructure.getDockerHostList());
        DockerHostLocation hostLoc = dockerHost.getDynamicLocation();

        DockerContainerLocation containerLoc = hostLoc.obtain();
        DockerContainer container = (DockerContainer) Iterables.getOnlyElement(dockerHost.getDockerContainerList());
        assertNotNull(container);
        hostLoc.release(containerLoc);
        assertTrue(dockerHost.getDockerContainerList().isEmpty(), "containers="+dockerHost.getDockerContainerList());
        //assertMembersEqualEventually(dockerInfrastructure.getContainerFabric(), ImmutableSet.<Entity>of());
    }

    private void assertMembersEqualEventually(final Group group, final Iterable<? extends Entity> entities) {
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                Asserts.assertEqualsIgnoringOrder(group.getMembers(), entities);
            }});
    }
    
    private LocationDefinition findLocationMatchingName(String regex) {
        List<String> contenders = Lists.newArrayList();
        for (Map.Entry<String, LocationDefinition> entry : managementContext.getLocationRegistry().getDefinedLocations().entrySet()) {
            String name = entry.getValue().getName();
            if (name.matches(regex)) {
                return entry.getValue();
            }
            contenders.add(name);
        }
        throw new NoSuchElementException("No location matching regex: "+regex+"; contenders were "+contenders);
    }
}
