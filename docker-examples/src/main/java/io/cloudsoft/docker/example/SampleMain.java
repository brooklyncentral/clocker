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

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects.ToStringHelper;

import brooklyn.catalog.BrooklynCatalog;
import brooklyn.cli.Main;
import io.airlift.command.Command;
import io.airlift.command.Option;

/**
 * This class provides a static main entry point for launching a custom Brooklyn-based app.
 * <p>
 * It inherits the standard Brooklyn CLI options from {@link Main},
 * plus adds a few more shortcuts for favourite blueprints to the {@link LaunchCommand}.
 */
public class SampleMain extends Main {

    private static final Logger log = LoggerFactory.getLogger(SampleMain.class);

    public static final String DEFAULT_LOCATION = "localhost";

    public static void main(String... args) {
        log.debug("CLI invoked with args "+ Arrays.asList(args));
        new SampleMain().execCli(args);
    }

    @Override
    protected String cliScriptName() {
        return "start.sh";
    }

    @Override
    protected Class<? extends BrooklynCommand> cliLaunchCommand() {
        return LaunchCommand.class;
    }

    @Command(name = "launch", description = "Starts a brooklyn server, and optionally an application. "
            + "Use --docker to deploy a Docker entity on the cloud.\n"
            + "Use --single or --cluster to launch one-node and clustered variants of the sample web application.")
    public static class LaunchCommand extends Main.LaunchCommand {

        // add these options to the LaunchCommand as shortcuts for our favourite applications
        @Option(name = { "--docker" }, description = "Deploy a single docker server instance")
        public boolean docker;

        @Option(name = { "--single" }, description = "Launch a single web-server instance")
        public boolean single;

        @Option(name = { "--cluster" }, description = "Launch a web-server cluster")
        public boolean cluster;

        @Override
        public Void call() throws Exception {
            // process our CLI arguments
            if (docker) setAppToLaunch(SingleDockerHostExample.class.getCanonicalName() );
            if (single) setAppToLaunch(SingleWebServerExample.class.getCanonicalName() );
            if (cluster) setAppToLaunch(WebClusterDatabaseExample.class.getCanonicalName() );

            // now process the standard launch arguments
            return super.call();
        }

        @Override
        protected void populateCatalog(BrooklynCatalog catalog) {
            super.populateCatalog(catalog);
            catalog.addItem(SingleDockerHostExample.class);
            catalog.addItem(SingleWebServerExample.class);
            catalog.addItem(WebClusterDatabaseExample.class);
        }

        @Override
        public ToStringHelper string() {
            return super.string()
                    .add("docker", docker)
                    .add("single", single)
                    .add("cluster", cluster);
        }
    }
}
