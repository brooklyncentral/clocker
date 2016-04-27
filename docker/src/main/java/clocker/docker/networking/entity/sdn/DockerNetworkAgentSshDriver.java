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
package clocker.docker.networking.entity.sdn;

import java.net.InetAddress;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.entity.software.base.AbstractSoftwareProcessSshDriver;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.net.Cidr;
import org.apache.brooklyn.util.net.Networking;
import org.apache.brooklyn.util.text.StringFunctions;

public abstract class DockerNetworkAgentSshDriver extends AbstractSoftwareProcessSshDriver implements DockerNetworkAgentDriver {

    private static final Logger LOG = LoggerFactory.getLogger(SdnAgent.class);

    public DockerNetworkAgentSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public List<String> getDockerNetworkCreateOpts() {
        List<String> opts = MutableList.of();
        return opts;
    }

    @Override
    public void createSubnet(String subnetId, Cidr subnetCidr) {
        Iterable<String> opts = Iterables.transform(getDockerNetworkCreateOpts(), StringFunctions.prepend("--opt "));
        getEntity().sensors().get(SdnAgent.DOCKER_HOST).runDockerCommand(
                String.format("network create --driver %s %s --subnet=%s %s",
                        getDockerNetworkDriver(), Joiner.on(' ').join(opts), subnetCidr, subnetId));
    }

    @Override
    public void deleteSubnet(String subnetId) {
        getEntity().sensors().get(SdnAgent.DOCKER_HOST).runDockerCommand(
                String.format("network rm --driver %s %s", getDockerNetworkDriver(), subnetId));
    }

    @Override
    public InetAddress attachNetwork(String containerId, String subnetId) {
        // Attach the container to the network
        getEntity().sensors().get(SdnAgent.DOCKER_HOST).runDockerCommand(
                String.format("network connect %s %s", subnetId, containerId));

        // Look up the container address on the network
        String ip = getEntity().sensors().get(SdnAgent.DOCKER_HOST).runDockerCommand(
                String.format("inspect --format \"{{ .NetworkSettings.Networks.%s.IPAddress }}\" %s", subnetId, containerId));
        InetAddress address = Networking.getInetAddressWithFixedName(ip);

        // Return the containers IP address
        getEntity().sensors().get(SdnAgent.SDN_PROVIDER).recordContainerAddress(subnetId, address);
        getEntity().sensors().get(SdnAgent.SDN_PROVIDER).associateContainerAddress(containerId, address);
        return address;
    }

}
