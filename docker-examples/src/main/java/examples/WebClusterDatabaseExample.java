package examples;

import brooklyn.enricher.Enrichers;
import brooklyn.enricher.HttpLatencyDetector;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.database.mysql.MySqlNode;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster;
import brooklyn.entity.webapp.DynamicWebAppCluster;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.entity.webapp.WebAppServiceConstants;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.location.access.PortForwardManager;
import brooklyn.location.basic.PortRanges;
import brooklyn.policy.autoscaling.AutoScalerPolicy;
import brooklyn.util.CommandLineUtil;
import brooklyn.util.net.Cidr;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import io.cloudsoft.networking.portforwarding.DockerPortForwarder;
import io.cloudsoft.networking.portforwarding.subnet.SubnetTierDockerImpl;
import io.cloudsoft.networking.subnet.PortForwarder;
import io.cloudsoft.networking.subnet.SubnetTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static brooklyn.entity.java.JavaEntityMethods.javaSysProp;
import static brooklyn.event.basic.DependentConfiguration.attributeWhenReady;
import static brooklyn.event.basic.DependentConfiguration.formatString;

/**
 * Launches a 3-tier app with nginx, clustered jboss, and mysql.
 **/
public class WebClusterDatabaseExample extends AbstractApplication {

    public static final Logger LOG = LoggerFactory.getLogger(WebClusterDatabaseExample.class);

    public static final String WAR_PATH = "classpath://hello-world-sql-webapp.war";

    public static final String DB_SETUP_SQL_URL = "classpath://visitors-creation-script.sql";

    public static final String DB_TABLE = "visitors";
    public static final String DB_USERNAME = "brooklyn";
    public static final String DB_PASSWORD = "br00k11n";

    public static final AttributeSensor<Integer> APPSERVERS_COUNT = Sensors.newIntegerSensor(
            "appservers.count", "Number of app servers deployed");

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

        MySqlNode mysql = subnetTier.addChild(EntitySpec.create(MySqlNode.class)
                .configure("creationScriptUrl", DB_SETUP_SQL_URL));

        ControlledDynamicWebAppCluster web = subnetTier.addChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure(WebAppService.HTTP_PORT, PortRanges.fromString("8080+"))
                .configure(JavaWebAppService.ROOT_WAR, WAR_PATH)
                .configure(javaSysProp("brooklyn.example.db.url"),
                        formatString("jdbc:%s%s?user=%s\\&password=%s",
                                attributeWhenReady(mysql, MySqlNode.DATASTORE_URL),
                                DB_TABLE, DB_USERNAME, DB_PASSWORD)));

        web.addEnricher(HttpLatencyDetector.builder().
                url(ControlledDynamicWebAppCluster.ROOT_URL).
                rollup(10, TimeUnit.SECONDS).
                build());

        // simple scaling policy
        web.getCluster().addPolicy(AutoScalerPolicy.builder().
                metric(DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW_PER_NODE).
                metricRange(10, 100).
                sizeRange(1, 5).
                build());

        // expose some KPI's
        addEnricher(Enrichers.builder()
                .propagating(WebAppServiceConstants.ROOT_URL,
                        DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW,
                        HttpLatencyDetector.REQUEST_LATENCY_IN_SECONDS_IN_WINDOW)
                .from(web)
                .build());

        addEnricher(Enrichers.builder()
                .propagating(ImmutableMap.of(DynamicWebAppCluster.GROUP_SIZE, APPSERVERS_COUNT))
                .from(web)
                .build());

    }

    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", "localhost");

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                .application(EntitySpec.create(StartableApplication.class, WebClusterDatabaseExample.class).displayName("Brooklyn WebApp Cluster with Database example"))
                .webconsolePort(port)
                .location(location)
                .start();

        Entities.dumpInfo(launcher.getApplications());
    }
}
