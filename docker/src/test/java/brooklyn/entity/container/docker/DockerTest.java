package brooklyn.entity.container.docker;

import org.testng.Assert;
import org.testng.annotations.Test;

public class DockerTest {

    @Test
    public void testObtainSpecificPort() {
        String portMap = "map[10999/tcp:[map[HostIp:0.0.0.0 HostPort:49153]] 22/tcp:[map[HostIp:0.0.0.0 HostPort:49154]] 8080/tcp:[map[HostIp:0.0.0.0 HostPort:49155]] 8443/tcp:[map[HostIp:0.0.0.0 HostPort:49156]] 9443/tcp:[map[HostIp:0.0.0.0 HostPort:49157]] 9990/tcp:[map[HostIp:0.0.0.0 HostPort:49158]]]";
        int portNumber = 22;
        int i = portMap.indexOf(portNumber + "/tcp:[");
        if (i == -1) throw new IllegalStateException();
        int j = portMap.substring(i).indexOf("HostPort:");
        int k = portMap.substring(i + j).indexOf("]]");
        String hostPort = portMap.substring(i + j + "HostPort:".length(), i + j + k);

        Assert.assertEquals("49154", hostPort);
    }

}
    