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

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.Enrichers;
import brooklyn.enricher.HttpLatencyDetector;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.container.docker.DockerAttributes;
import brooklyn.entity.database.DatastoreMixins;
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
import brooklyn.location.basic.PortRanges;
import brooklyn.policy.autoscaling.AutoScalerPolicy;

import com.google.common.collect.ImmutableMap;

/**
 * Launches a 3-tier app with nginx, clustered jboss, and mysql.
 */
public class WebClusterDatabaseExample extends AbstractApplication {

    public static final Logger LOG = LoggerFactory.getLogger(WebClusterDatabaseExample.class);

    public static final String WAR_PATH = "https://s3-eu-west-1.amazonaws.com/brooklyn-waratek/hello-world-sql.war";
    public static final String DB_SETUP_SQL_URL = "https://s3-eu-west-1.amazonaws.com/brooklyn-waratek/visitors-creation-script.sql";

    public static final String DB_TABLE = "visitors";
    public static final String DB_USERNAME = "brooklyn";
    public static final String DB_PASSWORD = "br00k11n";

    public static final AttributeSensor<Integer> APPSERVERS_COUNT = Sensors.newIntegerSensor("appservers.count", "Number of app servers deployed");

    @Override
    public void init() {
        AttributeSensor<String> mappedWebUrl = Sensors.newSensorWithPrefix("mapped.", WebAppService.ROOT_URL);
        AttributeSensor<String> mappedDatastoreUrl = Sensors.newSensorWithPrefix("mapped.", DatastoreMixins.DATASTORE_URL);
        AttributeSensor<String> mappedHostAndPortAttribute = Sensors.newStringSensor("mapped." + Attributes.HTTP_PORT.getName(), "Docker HTTP port mapping");

        MySqlNode mysql = addChild(EntitySpec.create(MySqlNode.class)
                .configure("creationScriptUrl", DB_SETUP_SQL_URL));

        ControlledDynamicWebAppCluster web = addChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure(ControlledDynamicWebAppCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class)
                        .configure(DockerAttributes.DOCKERFILE_URL, "classpath://brooklyn/entity/container/docker/ubuntu/UsesJavaDockerfile")
                        .configure(WebAppService.HTTP_PORT, PortRanges.fromString("8080+"))
                        .configure(JavaWebAppService.ROOT_WAR, WAR_PATH)
                        .configure(javaSysProp("brooklyn.example.db.url"),
                                formatString("jdbc:%s%s?user=%s\\&password=%s",
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
        web.getCluster().addPolicy(AutoScalerPolicy.builder().
                metric(DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW_PER_NODE).
                metricRange(10, 100).
                sizeRange(1, 5).
                build());

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
