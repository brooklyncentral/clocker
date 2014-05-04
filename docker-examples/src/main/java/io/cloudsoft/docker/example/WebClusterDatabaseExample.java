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

import static brooklyn.entity.java.JavaEntityMethods.javaSysProp;
import static brooklyn.event.basic.DependentConfiguration.attributeWhenReady;
import static brooklyn.event.basic.DependentConfiguration.formatString;
import static com.google.common.base.Preconditions.checkState;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import brooklyn.enricher.Enrichers;
import brooklyn.enricher.HttpLatencyDetector;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.database.mysql.MySqlNode;
import brooklyn.entity.proxy.nginx.NginxController;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster;
import brooklyn.entity.webapp.DynamicWebAppCluster;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.entity.webapp.jboss.JBoss7Server;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.location.Location;
import brooklyn.location.access.PortForwardManager;
import brooklyn.location.basic.PortRanges;
import brooklyn.location.docker.DockerLocation;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.policy.autoscaling.AutoScalerPolicy;
import brooklyn.util.CommandLineUtil;
import brooklyn.util.net.Cidr;
import io.cloudsoft.networking.portforwarding.DockerPortForwarder;
import io.cloudsoft.networking.subnet.SubnetTier;
import io.cloudsoft.networking.subnet.SubnetTierImpl;

/**
 * Launches a 3-tier app with nginx, clustered jboss, and mysql.
 *
 * By default, the example will point to a Docker instance running at 192.168.42.43:4243
 *
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

    private DockerPortForwarder portForwarder;

    @Override
    public void init() {
        AttributeSensor<String> mappedUrlAttribute = Sensors.newStringSensor("url.mapped");
        AttributeSensor<String> mappedHostAndPortAttribute = Sensors.newStringSensor("hostAndPort.mapped");
        
        portForwarder = new DockerPortForwarder(new PortForwardManager());

        SubnetTier subnetTier = addChild(EntitySpec.create(SubnetTier.class)
                .impl(SubnetTierImpl.class)
                .configure(SubnetTier.PORT_FORWARDER, portForwarder)
                .configure(SubnetTier.SUBNET_CIDR, Cidr.UNIVERSAL));

        MySqlNode mysql = subnetTier.addChild(EntitySpec.create(MySqlNode.class)
                .configure("creationScriptUrl", DB_SETUP_SQL_URL)
                .enricher(subnetTier.uriTransformingEnricher(MySqlNode.DATASTORE_URL, mappedUrlAttribute)));

        ControlledDynamicWebAppCluster web = subnetTier.addChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure(ControlledDynamicWebAppCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class)
                        .configure(WebAppService.HTTP_PORT, PortRanges.fromString("8080+"))
                        .configure(JavaWebAppService.ROOT_WAR, WAR_PATH)
                        .configure(javaSysProp("brooklyn.example.db.url"),
                                formatString("jdbc:%s%s?user=%s\\&password=%s",
                                        attributeWhenReady(mysql, mappedUrlAttribute),
                                        DB_TABLE, DB_USERNAME, DB_PASSWORD))
                        .enricher(subnetTier.hostAndPortTransformingEnricher(JBoss7Server.HTTP_PORT, mappedHostAndPortAttribute))
                        .enricher(subnetTier.uriTransformingEnricher(NginxController.ROOT_URL, mappedUrlAttribute)))
                .configure(ControlledDynamicWebAppCluster.CONTROLLER_SPEC, EntitySpec.create(NginxController.class)
                        .configure(NginxController.HOST_AND_PORT_SENSOR, mappedHostAndPortAttribute)
                        .enricher(subnetTier.uriTransformingEnricher(NginxController.ROOT_URL, mappedUrlAttribute))));

        web.addEnricher(Enrichers.builder()
                .propagating(mappedUrlAttribute)
                .from(web.getController())
                .build());

        web.addEnricher(HttpLatencyDetector.builder().
                url(mappedUrlAttribute).
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
                .propagating(mappedUrlAttribute,
                        DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW,
                        HttpLatencyDetector.REQUEST_LATENCY_IN_SECONDS_IN_WINDOW)
                .from(web)
                .build());

        addEnricher(Enrichers.builder()
                .propagating(ImmutableMap.of(DynamicWebAppCluster.GROUP_SIZE, APPSERVERS_COUNT))
                .from(web)
                .build());
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        Location location = Iterables.getOnlyElement(locations);
        JcloudsLocation loc;
        if (location instanceof DockerLocation) {
            loc = (JcloudsLocation) ((DockerLocation) location).getProvisioner();
        } else if (location instanceof JcloudsLocation) {
            loc = (JcloudsLocation) location;
        } else {
            throw new IllegalStateException("Expected jcloudsLocation or DockerLocation");
        }
        checkState("docker".equals(loc.getProvider()), "Expected docker rather than provider %s", loc.getProvider());
        portForwarder.init(URI.create(loc.getEndpoint()));
        super.start(locations);
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
