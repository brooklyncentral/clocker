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

import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppLiveTestSupport;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.docker.DockerLocation;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * Brooklyn managed basic Docker infrastructure.
 */
public class DockerInfrastructureLiveTest extends BrooklynAppLiveTestSupport  {

    private static final String DEFAULT_LOCATION_SPEC = "jclouds:softlayer:ams01";
    private static final Logger LOG = LoggerFactory.getLogger(DockerInfrastructureLiveTest.class);

    private BrooklynLauncher launcher;
    private Location testLocation;
    private String testLocationSpec;

    @BeforeClass(alwaysRun = true)
    @Parameters({"locationSpec"})
    public void setSpec(@Optional String locationSpec) {
        testLocationSpec = Strings.isBlank(locationSpec) ? DEFAULT_LOCATION_SPEC : locationSpec;
        LOG.info("Running {} in {}", getClass().getName(), testLocationSpec);
    }

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        super.setUp();
        testLocation = mgmt.getLocationRegistry().resolve(testLocationSpec);
        launcher = BrooklynLauncher.newInstance()
                .managementContext(mgmt)
                .start();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (mgmt != null) Entities.destroyAll(mgmt);
        if (launcher != null) launcher.terminate();
    }

    @Test(groups="Live")
    public void testRegistersLocation() throws Exception {
        app.createAndManageChild(EntitySpec.create(DockerInfrastructure.class)
                .configure(DockerInfrastructure.DOCKER_HOST_CLUSTER_MIN_SIZE, 1)
                .configure(DockerInfrastructure.LOCATION_NAME_PREFIX, "dynamicdockertest")
                .configure(DockerInfrastructure.SDN_ENABLE, false)
                .displayName("Docker Infrastructure"));
        app.start(ImmutableList.of(testLocation));

        LocationDefinition infraLocDef = findLocationMatchingName("dynamicdockertest.*");
        Location infraLoc = mgmt.getLocationRegistry().resolve(infraLocDef);
        assertTrue(infraLoc instanceof DockerLocation, "loc="+infraLoc);
    }

    @Test(groups="Live")
    public void testDeploysTrivialApplication() throws Exception {
        DockerInfrastructureTests.testDeploysTrivialApplication(app, testLocation);
    }

    private LocationDefinition findLocationMatchingName(String regex) {
        List<String> contenders = Lists.newArrayList();
        for (Map.Entry<String, LocationDefinition> entry : mgmt.getLocationRegistry().getDefinedLocations().entrySet()) {
            String name = entry.getValue().getName();
            if (name.matches(regex)) {
                return entry.getValue();
            }
            contenders.add(name);
        }
        throw new NoSuchElementException("No location matching regex: "+regex+"; contenders were "+contenders);
    }
}
