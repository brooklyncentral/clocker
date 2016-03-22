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
package brooklyn.entity.container.docker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.test.BrooklynAppLiveTestSupport;
import org.apache.brooklyn.launcher.BrooklynLauncher;
import org.apache.brooklyn.util.text.Strings;

public class AbstractClockerIntegrationTest extends BrooklynAppLiveTestSupport {

    private static final String DEFAULT_LOCATION_SPEC = "jclouds:softlayer:ams01";
    private static final Logger LOG = LoggerFactory.getLogger(AbstractClockerIntegrationTest.class);

    protected BrooklynLauncher launcher;
    protected Location testLocation;
    protected String testLocationSpec;

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
        super.tearDown();
        if (launcher != null) launcher.terminate();
    }
}
