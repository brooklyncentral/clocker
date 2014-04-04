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

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import brooklyn.entity.AbstractSoftlayerLiveTest;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;

@Test(groups="Live")
public class DockerSoftLayerLiveTest extends AbstractSoftlayerLiveTest {

    @Override
    protected void doTest(Location loc) throws Exception {
        app.createAndManageChild(EntitySpec.create(DockerHost.class).configure("docker.port", "4244+"));
        app.start(ImmutableList.of(loc));
    }

    @Test(enabled=false)
    public void testDummy() { } // Convince testng IDE integration that this really does have test methods

    @Override
    @Test(enabled=false)
    public void test_Default() throws Exception {
    }

    @Override
    @Test(enabled=false)
    public void test_Centos_6_0() throws Exception {
    }

}
