/*
 * Copyright 2014-2015 by Cloudsoft Corporation Limited
 */
package brooklyn.networking.sdn;

import java.net.InetAddress;

import brooklyn.entity.basic.SoftwareProcessDriver;
import brooklyn.util.net.Cidr;

public interface SdnAgentDriver extends SoftwareProcessDriver {

    void createSubnet(String virtualNetworkId, String subnetId, Cidr subnetCidr);

    InetAddress attachNetwork(String containerId, String subnetId);

}
