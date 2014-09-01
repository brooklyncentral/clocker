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
package brooklyn.clocker.example;

import io.airlift.command.Command;
import io.airlift.command.Option;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.BrooklynCatalog;
import brooklyn.cli.Main;
import brooklyn.demo.NodeJsTodoApplication;

import com.google.common.base.Objects.ToStringHelper;

/**
 * Launch the Clocker service.
 */
public class ClockerMain extends Main {

    private static final Logger log = LoggerFactory.getLogger(ClockerMain.class);

    public static void main(String... args) {
        log.debug("CLI invoked with args "+ Arrays.asList(args));
        new ClockerMain().execCli(args);
    }

    @Override
    protected String cliScriptName() {
        return "clocker.sh";
    }

    @Override
    protected Class<? extends BrooklynCommand> cliLaunchCommand() {
        return LaunchCommand.class;
    }

    @Command(name = "launch", description = "Starts a Brooklyn server, and optionally an application. " +
            "Use --cloud to launch a Docker cloud infrastructure.")
    public static class LaunchCommand extends Main.LaunchCommand {

        @Option(name = { "--cloud" }, description = "Launch a Docker cloud infrastructure")
        public boolean cloud;

        @Override
        public Void call() throws Exception {
            // process our CLI arguments
            if (cloud) setAppToLaunch(DockerCloud.class.getCanonicalName() );

            // now process the standard launch arguments
            return super.call();
        }

        @SuppressWarnings("deprecation")
        @Override
        protected void populateCatalog(BrooklynCatalog catalog) {
            super.populateCatalog(catalog);
            catalog.addItem(DockerCloud.class);
            catalog.addItem(JBossApplication.class);
            catalog.addItem(NodeJsTodoApplication.class);
            catalog.addItem(ActiveMQApplication.class);
            catalog.addItem(TomcatApplication.class);
            catalog.addItem(TomcatClusterWithMySql.class);
        }

        @Override
        public ToStringHelper string() {
            return super.string().add("cloud", cloud);
        }
    }
}
