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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.jboss.JBoss7Server;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.location.basic.PortRanges;
import brooklyn.util.CommandLineUtil;

import com.google.common.collect.Lists;

/**
 * This example starts a web app on 8080, waits for a keypress, then stops it.
 */
public class SingleWebServerExample extends AbstractApplication implements StartableApplication {

    public static final Logger LOG = LoggerFactory.getLogger(SingleWebServerExample.class);

    private static final String WAR_PATH = "https://s3-eu-west-1.amazonaws.com/brooklyn-waratek/hello-world.war";

    @Override
    public void init() {
        addChild(EntitySpec.create(JBoss7Server.class)
                .configure(JavaWebAppService.ROOT_WAR, WAR_PATH)
                .configure(Attributes.HTTP_PORT, PortRanges.fromString("8080+")));
    }

    public static void main(String[] argv) throws Exception {
        List<String> args = Lists.newArrayList(argv);
        String port = CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", "SingleDockerHostExample");

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                .application(EntitySpec.create(StartableApplication.class, SingleWebServerExample.class).displayName("Brooklyn WebApp example"))
                .webconsolePort(port)
                .location(location)
                .start();

        Entities.dumpInfo(launcher.getApplications());
    }

}
