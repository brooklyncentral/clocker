package brooklyn.entity.container.docker;

import java.net.InetAddress;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.net.Cidr;

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
