/*
 * Copyright 2012-2014 by Cloudsoft Corporation Limted
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
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.container.docker.DockerAttributes;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.java.UsesJmx.JmxAgentModes;
import brooklyn.entity.messaging.activemq.ActiveMQBroker;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.location.basic.PortRanges;

@Catalog(name="ActiveMQ Broker",
        description="Single ActiveMQ message broker",
        iconUrl="classpath://activemq-logo.png")
public class ActiveMQApplication extends AbstractApplication {

    @CatalogConfig(label="OpenWire Port", priority=0)
    public static final PortAttributeSensorAndConfigKey OPEN_WIRE_PORT = ActiveMQBroker.OPEN_WIRE_PORT;

    @CatalogConfig(label="Jetty Port", priority=0)
    public static final PortAttributeSensorAndConfigKey AMQ_JETTY_PORT = ActiveMQBroker.AMQ_JETTY_PORT;

    @Override
    public void init() {
        addChild(EntitySpec.create(ActiveMQBroker.class)
                .displayName("ActiveMQ Broker")
                .configure(DockerAttributes.DOCKERFILE_URL, "https://s3-eu-west-1.amazonaws.com/brooklyn-docker/UsesJavaDockerfile")
                .configure(UsesJmx.USE_JMX, Boolean.TRUE)
                .configure(UsesJmx.JMX_AGENT_MODE, JmxAgentModes.JMXMP)
                .configure(UsesJmx.JMX_PORT, PortRanges.fromString("30000+"))
                .configure(ActiveMQBroker.OPEN_WIRE_PORT, getConfig(OPEN_WIRE_PORT))
                .configure(ActiveMQBroker.AMQ_JETTY_PORT, getConfig(AMQ_JETTY_PORT)));
    }
}
