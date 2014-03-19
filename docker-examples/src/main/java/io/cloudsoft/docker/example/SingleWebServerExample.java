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

import static com.google.common.base.Preconditions.checkState;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.jboss.JBoss7Server;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.location.Location;
import brooklyn.location.access.PortForwardManager;
import brooklyn.location.basic.PortRanges;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.util.CommandLineUtil;
import brooklyn.util.net.Cidr;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import io.cloudsoft.networking.portforwarding.DockerPortForwarder;
import io.cloudsoft.networking.portforwarding.subnet.SubnetTierDockerImpl;
import io.cloudsoft.networking.subnet.SubnetTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;
import java.util.Collection;
import java.util.List;

/**
 * This example starts a web app on 8080, waits for a keypress, then stops it.
 *
 * By default, the example will point to a Docker instance running at 192.168.42.43:4243
 *
 */
public class SingleWebServerExample extends AbstractApplication implements StartableApplication {

    public static final Logger LOG = LoggerFactory.getLogger(SingleWebServerExample.class);

    private static final String WAR_PATH = "classpath://hello-world-webapp.war";

    private DockerPortForwarder portForwarder;

    @Override
    public void init() {
        portForwarder = new DockerPortForwarder(this, new PortForwardManager());

        SubnetTier subnetTier = addChild(EntitySpec.create(SubnetTier.class)
                .impl(SubnetTierDockerImpl.class)
                .configure(SubnetTier.PORT_FORWARDER, portForwarder)
                .configure(SubnetTier.SUBNET_CIDR, Cidr.UNIVERSAL));

        subnetTier.addChild(EntitySpec.create(JBoss7Server.class)
                .configure(JavaWebAppService.ROOT_WAR, WAR_PATH)
                .configure(Attributes.HTTP_PORT, PortRanges.fromString("8080+")));
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        JcloudsLocation loc = (JcloudsLocation) Iterables.getOnlyElement(locations);
        checkState("docker".equals(loc.getProvider()), "Expected docker rather than provider %s", loc.getProvider());
        portForwarder.init(URI.create(loc.getEndpoint()));
        super.start(locations);
    }

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
