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
package clocker.main;

import io.airlift.command.Cli.CliBuilder;
import io.airlift.command.Command;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.brooklyn.launcher.BrooklynLauncher;

public class Main extends org.apache.brooklyn.cli.Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String... args) {
        log.debug("CLI invoked with args "+Arrays.asList(args));
        new Main().execCli(args);
    }

    @Override
    protected CliBuilder<BrooklynCommand> cliBuilder() {
        return super.cliBuilder().withCommand(LaunchClocker.class);
    }

    @Command(name = "clocker", description = "Starts the Brooklyn server with the Clocker console")
    public static class LaunchClocker extends org.apache.brooklyn.cli.Main.LaunchCommand {
        @Override
        protected BrooklynLauncher createLauncher() {
            return super.createLauncher()
                    .webapp("/",            "brooklyn-clocker-jsgui.war")
                    .webapp("/brooklyn",    "brooklyn-jsgui.war")
                    .webapp("/clocker",     "brooklyn-clocker-console.war");
        }
    }

}
