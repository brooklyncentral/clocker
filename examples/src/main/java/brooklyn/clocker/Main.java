package brooklyn.clocker;

import io.airlift.command.Command;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.launcher.BrooklynLauncher;


public class Main extends brooklyn.cli.Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String... args) {
        log.debug("CLI invoked with args "+Arrays.asList(args));
        new Main().execCli(args);
    }

    @Override
    protected String cliScriptName() {
        return "clocker";
    }
    
    @Override
    protected Class<? extends BrooklynCommand> cliLaunchCommand() {
        return LaunchCommand.class;
    }

    @Command(name = "launch", description = "Starts Clocker.")
    public static class LaunchCommand extends brooklyn.cli.Main.LaunchCommand {
        @Override
        protected BrooklynLauncher createLauncher() {
            return super.createLauncher()
                    .webapp("/", "brooklyn-jsgui.war")
                    .webapp("/clocker", "brooklyn-clocker-console.war");
        }
    }

}
