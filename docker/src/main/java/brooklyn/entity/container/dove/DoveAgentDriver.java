/*
 * Copyright 2014 by Cloudsoft Corporation Limited
 */
package brooklyn.entity.container.dove;

import java.net.InetAddress;

import brooklyn.entity.basic.SoftwareProcessDriver;

public interface DoveAgentDriver extends SoftwareProcessDriver {

    InetAddress attachNetwork(String containerId);

}
