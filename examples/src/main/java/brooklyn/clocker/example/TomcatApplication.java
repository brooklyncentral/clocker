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

import brooklyn.catalog.Catalog;
import brooklyn.catalog.CatalogConfig;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.container.docker.DockerAttributes;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.java.UsesJmx.JmxAgentModes;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.entity.webapp.tomcat.TomcatServer;
import brooklyn.location.basic.PortRanges;

/**
 * Single-node Tomcat server instance.
 */
@Catalog(name="Tomcat",
        description="Single Tomcat server.",
        iconUrl="classpath://tomcat-logo.png")
public class TomcatApplication extends AbstractApplication implements StartableApplication {

    public static final String DEFAULT_WAR_PATH = "https://s3-eu-west-1.amazonaws.com/brooklyn-clocker/hello-world.war";

    @CatalogConfig(label="War File (URL)", priority=0)
    public static final ConfigKey<String> WAR_PATH = ConfigKeys.newConfigKey(
            "app.war", "URL to the application archive which should be deployed", DEFAULT_WAR_PATH);

    @Override
    public void init() {
        addChild(EntitySpec.create(TomcatServer.class)
                .displayName("Tomcat Server")
                .configure(DockerAttributes.DOCKERFILE_URL, "https://s3-eu-west-1.amazonaws.com/brooklyn-clocker/UsesJavaDockerfile")
                .configure(DockerAttributes.DOCKERFILE_NAME, "ubuntujava")
                .configure(WebAppService.HTTP_PORT, PortRanges.fromString("8080+"))
                .configure(JavaWebAppService.ROOT_WAR, Entities.getRequiredUrlConfig(this, WAR_PATH))
                .configure(SoftwareProcess.SUGGESTED_VERSION, "7.0.53")
                .configure(UsesJmx.USE_JMX, Boolean.TRUE)
                .configure(UsesJmx.JMX_AGENT_MODE, JmxAgentModes.JMXMP)
                .configure(UsesJmx.JMX_PORT, PortRanges.fromString("30000+")));
    }

}
