/*
 * Copyright 2014 by Cloudsoft Corporation Limited
 */
package brooklyn.entity.container.weave;

import java.net.InetAddress;

import brooklyn.entity.basic.SoftwareProcessDriver;

public interface WeaveContainerDriver extends SoftwareProcessDriver {

    InetAddress attachNetwork(String containerId);

}
