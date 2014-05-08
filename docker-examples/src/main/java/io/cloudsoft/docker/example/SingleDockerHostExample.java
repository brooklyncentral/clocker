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
package io.cloudsoft.docker.example;

import java.util.List;

import com.google.common.collect.Lists;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.location.dynamic.LocationOwner;
import brooklyn.util.CommandLineUtil;

public class SingleDockerHostExample extends AbstractApplication {

    @Override
    public void init() {
        addChild(EntitySpec.create(DockerInfrastructure.class)
                .configure(LocationOwner.LOCATION_NAME, "SingleDockerHostExample")
                .configure(DockerInfrastructure.DOCKER_HOST_CLUSTER_MIN_SIZE, 1));
    }

    public static void main(String[] argv) throws Exception {
        List<String> args = Lists.newArrayList(argv);
        String port = CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", "localhost");

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                .application(EntitySpec.create(StartableApplication.class, SingleDockerHostExample.class).displayName("Brooklyn Single Docker Host Example"))
                .webconsolePort(port)
                .location(location)
                .start();

        Entities.dumpInfo(launcher.getApplications());
    }

}

