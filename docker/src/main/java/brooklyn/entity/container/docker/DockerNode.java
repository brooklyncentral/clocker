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
package brooklyn.entity.container.docker;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.location.dynamic.LocationOwner;
import brooklyn.util.flags.SetFromFlag;

/**
 * @author Andrea Turli
 */
@Catalog(name="Docker Node", description="Docker is an open-source engine to easily create lightweight, portable, " +
        "self-sufficient containers from any application.",
        iconUrl="classpath:///docker-top-logo.png")
@ImplementedBy(DockerNodeImpl.class)
public interface DockerNode extends SoftwareProcess, LocationOwner {

   @SetFromFlag("version")
   ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "0.8");
   @SetFromFlag("dockerPort")
   PortAttributeSensorAndConfigKey DOCKER_PORT = new PortAttributeSensorAndConfigKey("docker.port",
           "Docker port", "4243+");
   BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
           SoftwareProcess.DOWNLOAD_URL, "https://get.docker.io/builds/Linux/x86_64/docker-latest");
   @SetFromFlag("socketUid")
   public static final BasicAttributeSensorAndConfigKey.StringAttributeSensorAndConfigKey SOCKET_UID =
           new BasicAttributeSensorAndConfigKey.StringAttributeSensorAndConfigKey("docker.socketUid",
                   "Socket uid, for use in file unix:///var/run/docker.sock", null);

}
