package docker;

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
