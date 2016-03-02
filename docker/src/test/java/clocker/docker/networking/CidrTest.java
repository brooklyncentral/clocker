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
package clocker.docker.networking;

import java.net.InetAddress;

import org.testng.Assert;
import org.testng.annotations.Test;

import clocker.docker.networking.entity.sdn.SdnProviderImpl;

import org.apache.brooklyn.util.net.Cidr;

public class CidrTest {

    /** 8 * 32 hosts in a /27 is 256 or a class C */
    @Test
    public void testCidr() {
        Cidr pool = Cidr.LINK_LOCAL;
        int size = 27, allocated = 8;
        Cidr subnet = getSubnet(pool, size, allocated);
        System.out.println("Subnet " + subnet);
        Assert.assertEquals(subnet, new Cidr("169.254.1.0/27"));
    }

    /** @see SdnProviderImpl#getSubnet(String, String) */
    public Cidr getSubnet(Cidr pool, int size, int allocated) {
        int offset = allocated * (1 << (32 - size));
        InetAddress base = pool.addressAtOffset(offset);
        Cidr subnet = new Cidr(base.getHostAddress() + "/" + size);
        return subnet;
    }
}
