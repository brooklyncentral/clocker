package examples;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.jboss.JBoss7Server;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.location.access.PortForwardManager;
import brooklyn.location.basic.PortRanges;
import brooklyn.util.CommandLineUtil;
import brooklyn.util.net.Cidr;
import com.google.common.collect.Lists;
import io.cloudsoft.networking.portforwarding.DockerPortForwarder;
import io.cloudsoft.networking.portforwarding.subnet.SubnetTierDockerImpl;
import io.cloudsoft.networking.subnet.PortForwarder;
import io.cloudsoft.networking.subnet.SubnetTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * This example starts one web app on 8080, waits for a keypress, then stops it.
 */
public class SingleWebServerExample extends AbstractApplication {

    public static final Logger LOG = LoggerFactory.getLogger(SingleWebServerExample.class);

    private static final String WAR_PATH = "classpath://hello-world-webapp.war";

    protected static final String DOCKER_HOST_IP = "192.168.42.43";
    protected static final int DOCKER_HOST_PORT = 4243;

    @Override
    public void init() {
        PortForwardManager portForwardManager = new PortForwardManager();
        PortForwarder portForwarder = new DockerPortForwarder(this, portForwardManager, DOCKER_HOST_IP,
                DOCKER_HOST_PORT);

        SubnetTier subnetTier = addChild(EntitySpec.create(SubnetTier.class)
                .impl(SubnetTierDockerImpl.class)
                .configure(SubnetTier.PORT_FORWARDER, portForwarder)
                .configure(SubnetTier.SUBNET_CIDR, Cidr.UNIVERSAL));

        subnetTier.addChild(EntitySpec.create(JBoss7Server.class)
                .configure(JavaWebAppService.ROOT_WAR, WAR_PATH)
                .configure(Attributes.HTTP_PORT, PortRanges.fromString("8080+")));
    }

    // Shows how to use ApplicationBuilder without sub-classing, but for CLI usage one should sub-class
    public static void main(String[] argv) throws Exception {
        List<String> args = Lists.newArrayList(argv);
        String port = CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", "localhost");

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                .application(EntitySpec.create(StartableApplication.class, SingleWebServerExample.class)
                        .displayName("Brooklyn WebApp example"))

                .webconsolePort(port)
                .location(location)
                .start();

        Entities.dumpInfo(launcher.getApplications());
    }

}
