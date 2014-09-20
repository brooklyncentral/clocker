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

import static brooklyn.entity.java.JavaEntityMethods.javaSysProp;
import static brooklyn.event.basic.DependentConfiguration.attributeWhenReady;
import static brooklyn.event.basic.DependentConfiguration.formatString;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.Catalog;
import brooklyn.catalog.CatalogConfig;
import brooklyn.config.ConfigKey;
import brooklyn.enricher.Enrichers;
import brooklyn.enricher.HttpLatencyDetector;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.container.DockerAttributes;
import brooklyn.entity.container.DockerUtils;
import brooklyn.entity.database.DatastoreMixins;
import brooklyn.entity.database.mysql.MySqlNode;
import brooklyn.entity.group.Cluster;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.java.UsesJmx.JmxAgentModes;
import brooklyn.entity.proxy.nginx.NginxController;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster;
import brooklyn.entity.webapp.DynamicWebAppCluster;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.entity.webapp.tomcat.TomcatServer;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.location.basic.PortRanges;
import brooklyn.policy.autoscaling.AutoScalerPolicy;
import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableMap;

/**
 * Launches a 3-tier app with nginx, clustered jboss, and mysql.
 */
@Catalog(name="Elastic Web Application",
        description="Deploys a WAR to an Nginx load-balanced Tomcat cluster, " +
                "with an auto-scaling policy, wired to a MySQL database.",
        iconUrl="classpath://glossy-3d-blue-web-icon.png")
public class TomcatClusterWithMySql extends AbstractApplication {

    public static final Logger LOG = LoggerFactory.getLogger(TomcatClusterWithMySql.class);

    public static final String DEFAULT_WAR_PATH = "https://s3-eu-west-1.amazonaws.com/brooklyn-clocker/hello-world-sql.war";
    public static final String DEFAULT_DB_SETUP_SQL_URL = "https://s3-eu-west-1.amazonaws.com/brooklyn-clocker/visitors-creation-script.sql";

    @CatalogConfig(label="WAR (URL)", priority=0)
    public static final ConfigKey<String> WAR_PATH = ConfigKeys.newConfigKey(
            "app.war", "URL to the application archive which should be deployed", DEFAULT_WAR_PATH);

    @CatalogConfig(label="DB Setup SQL (URL)", priority=0)
    public static final ConfigKey<String> DB_SETUP_SQL_URL = ConfigKeys.newConfigKey(
            "app.db_sql", "URL to the SQL script to set up the database", DEFAULT_DB_SETUP_SQL_URL);

    @CatalogConfig(label="Initial Cluster Size", priority=1)
    public static final ConfigKey<Integer> INITIAL_SIZE = ConfigKeys.newConfigKeyWithDefault(Cluster.INITIAL_SIZE, 2);

    public static final String DB_TABLE = "visitors";
    public static final String DB_USERNAME = "brooklyn";
    public static final String DB_PASSWORD = "br00k11n";

    public static final AttributeSensor<Integer> APPSERVERS_COUNT = Sensors.newIntegerSensor("appservers.count", "Number of app servers deployed");

    @Override
    public void initApp() {
        AttributeSensor<String> mappedWebUrl = DockerUtils.mappedSensor(WebAppService.ROOT_URL);
        AttributeSensor<String> mappedDatastoreUrl = DockerUtils.mappedSensor(DatastoreMixins.DATASTORE_URL);
        AttributeSensor<String> mappedHostAndPortAttribute = DockerUtils.mappedPortSensor(Attributes.HTTP_PORT);

        MySqlNode mysql = addChild(EntitySpec.create(MySqlNode.class)
                .configure("creationScriptUrl", Entities.getRequiredUrlConfig(this, DB_SETUP_SQL_URL)));

        ControlledDynamicWebAppCluster web = addChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure(Cluster.INITIAL_SIZE, getConfig(INITIAL_SIZE))
                .configure(ControlledDynamicWebAppCluster.MEMBER_SPEC, EntitySpec.create(TomcatServer.class)
                        .configure(DockerAttributes.DOCKERFILE_URL, "https://s3-eu-west-1.amazonaws.com/brooklyn-clocker/UsesJavaDockerfile")
                        .configure(WebAppService.HTTP_PORT, PortRanges.fromString("8080+"))
                        .configure(SoftwareProcess.SUGGESTED_VERSION, "7.0.53")
                        .configure(UsesJmx.USE_JMX, Boolean.TRUE)
                        .configure(UsesJmx.JMX_AGENT_MODE, JmxAgentModes.JMXMP)
                        .configure(UsesJmx.JMX_PORT, PortRanges.fromString("30000+"))
                        .configure(JavaWebAppService.ROOT_WAR, Entities.getRequiredUrlConfig(this, WAR_PATH))
                        .configure(javaSysProp("brooklyn.example.db.url"),
                                formatString("jdbc:%s%s?user=%s&password=%s",
                                        attributeWhenReady(mysql, mappedDatastoreUrl),
                                        DB_TABLE, DB_USERNAME, DB_PASSWORD)))
                .configure(ControlledDynamicWebAppCluster.CONTROLLER_SPEC, EntitySpec.create(NginxController.class)
                        .configure(NginxController.HOST_AND_PORT_SENSOR, mappedHostAndPortAttribute)));

        web.addEnricher(Enrichers.builder()
                .propagating(mappedWebUrl)
                .from(web.getController())
                .build());

        web.addEnricher(HttpLatencyDetector.builder().
                url(mappedWebUrl).
                rollup(10, TimeUnit.SECONDS).
                build());

        // simple scaling policy
        web.getCluster().addPolicy(AutoScalerPolicy.builder()
                .metric(DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW_PER_NODE)
                .metricRange(0.1, 10.0)
                .sizeRange(getConfig(INITIAL_SIZE), 10)
                .resizeDownStabilizationDelay(Duration.ONE_MINUTE.toMilliseconds())
                .resizeUpStabilizationDelay(Duration.THIRTY_SECONDS.toMilliseconds())
                .build());

        // expose some KPI's
        addEnricher(Enrichers.builder()
                .propagating(mappedWebUrl,
                        DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW,
                        HttpLatencyDetector.REQUEST_LATENCY_IN_SECONDS_IN_WINDOW)
                .from(web)
                .build());

        addEnricher(Enrichers.builder()
                .propagating(ImmutableMap.of(DynamicWebAppCluster.GROUP_SIZE, APPSERVERS_COUNT))
                .from(web)
                .build());
    }
}
