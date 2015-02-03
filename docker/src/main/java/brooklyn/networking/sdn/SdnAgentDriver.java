/*
 * Copyright 2014 by Cloudsoft Corporation Limited
 */
package brooklyn.networking.sdn;

import java.net.InetAddress;

import brooklyn.entity.basic.SoftwareProcessDriver;

public interface SdnAgentDriver extends SoftwareProcessDriver {

    InetAddress attachNetwork(String containerId, String subnetId, String subnetName);

}
