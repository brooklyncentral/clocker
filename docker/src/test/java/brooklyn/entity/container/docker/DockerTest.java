/*
 * Copyright 2014-2016 by Cloudsoft Corporation Limited
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
    
