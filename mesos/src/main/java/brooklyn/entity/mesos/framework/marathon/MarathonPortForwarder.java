/*
 * Copyright 2013-2015 by Cloudsoft Corporation Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.entity.mesos.framework.marathon;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.net.HostAndPort;
import com.google.inject.Module;

import org.jclouds.Constants;
import org.jclouds.ContextBuilder;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.docker.DockerApi;
import org.jclouds.docker.domain.Container;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.sshj.config.SshjSshClientModule;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.api.location.PortRange;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.location.access.PortForwardManager;
import org.apache.brooklyn.core.location.access.PortForwardManagerImpl;
import org.apache.brooklyn.core.location.access.PortMapping;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsSshMachineLocation;
import org.apache.brooklyn.util.net.Cidr;
import org.apache.brooklyn.util.net.HasNetworkAddresses;
import org.apache.brooklyn.util.net.Protocol;

import brooklyn.networking.common.subnet.PortForwarder;

public class MarathonPortForwarder implements PortForwarder {

    private static final Logger log = LoggerFactory.getLogger(MarathonPortForwarder.class);

    private PortForwardManager portForwardManager;
    private String dockerEndpoint;
    private String dockerHostname;
    private String dockerIdentity;
    private String dockerCredential;

    public MarathonPortForwarder() {
    }

    public MarathonPortForwarder(PortForwardManager portForwardManager) {
        this.portForwardManager = portForwardManager;
    }

    @Override
    public void injectManagementContext(ManagementContext managementContext) {
        if (portForwardManager == null) {
            portForwardManager = (PortForwardManager) managementContext.getLocationRegistry().resolve("portForwardManager(scope=global)");
        }
    }
    
    public void init(String dockerHostIp, int dockerHostPort) {
        this.dockerEndpoint = URI.create("http://" + dockerHostIp + ":" + dockerHostPort).toASCIIString();
        this.dockerHostname = dockerHostIp;
        this.dockerIdentity = "notused";
        this.dockerCredential = "notused";
    }

    public void init(URI endpoint) {
        init(endpoint, "notused", "notused");
    }

    public void init(URI endpoint, String identity, String credential) {
        this.dockerEndpoint = endpoint.toASCIIString();
        this.dockerHostname = endpoint.getHost();
        this.dockerIdentity = identity;
        this.dockerCredential = credential;
    }

    public void init(Iterable<? extends Location> locations) {
        Optional<? extends Location> jcloudsLoc = Iterables.tryFind(locations, Predicates.instanceOf(JcloudsLocation.class));
        String provider = (jcloudsLoc.isPresent()) ? ((JcloudsLocation)jcloudsLoc.get()).getProvider() : null;
        String endpoint = (jcloudsLoc.isPresent()) ? ((JcloudsLocation)jcloudsLoc.get()).getEndpoint() : null;
        String identity = (jcloudsLoc.isPresent()) ? ((JcloudsLocation)jcloudsLoc.get()).getIdentity() : null;
        String credential = (jcloudsLoc.isPresent()) ? ((JcloudsLocation)jcloudsLoc.get()).getCredential() : null;
        if (jcloudsLoc.isPresent() && "docker".equals(provider)) {
            init(URI.create(endpoint), identity, credential);
        } else {
            throw new IllegalStateException("Cannot infer docker host URI from locations: "+locations);
        }
    }

    @Override
    public void inject(Entity owner, List<Location> locations) {
        // no-op
    }

    @Override
    public PortForwardManager getPortForwardManager() {
        if (portForwardManager == null) {
            log.warn("Instantiating new PortForwardManager, because ManagementContext not injected into "+this
                    +" (deprecated behaviour that will not be supported in future versions)");
            portForwardManager = new PortForwardManagerImpl();
        }
        return portForwardManager;
    }

    @Override
    public String openGateway() {
        // IP of port-forwarder already exists
        return dockerHostname;
    }

    @Override
    public String openStaticNat(Entity serviceToOpen) {
        throw new UnsupportedOperationException("Can only open individual ports; not static nat with iptables");
    }

    @Override
    public void openFirewallPort(Entity entity, int port, Protocol protocol, Cidr accessingCidr) {
        // TODO If port is already open in docker port-mapping then no-op; otherwise UnsupportedOperationException currently
        if (log.isDebugEnabled()) log.debug("no-op in {} for openFirewallPort({}, {}, {}, {})", new Object[] {this, entity, port, protocol, accessingCidr});
    }

    @Override
    public void openFirewallPortRange(Entity entity, PortRange portRange, Protocol protocol, Cidr accessingCidr) {
        // TODO If port is already open in docker port-mapping then no-op; otherwise UnsupportedOperationException currently
        if (log.isDebugEnabled()) log.debug("no-op in {} for openFirewallPortRange({}, {}, {}, {})", new Object[] {this, entity, portRange, protocol, accessingCidr});
    }

    @Override
    public HostAndPort openPortForwarding(HasNetworkAddresses targetMachine, int targetPort, Optional<Integer> optionalPublicPort,
            Protocol protocol, Cidr accessingCidr) {

        String targetIp = Iterables.getFirst(Iterables.concat(targetMachine.getPrivateAddresses(), targetMachine.getPublicAddresses()), null);
        if (targetIp==null) {
            throw new IllegalStateException("Failed to open port-forwarding for machine "+targetMachine+" because its" +
                    " location has no target ip: "+targetMachine);
        }
        HostAndPort targetSide = HostAndPort.fromParts(targetIp, targetPort);
        HostAndPort newFrontEndpoint = openPortForwarding(targetSide, optionalPublicPort, protocol, accessingCidr);
        log.debug("Enabled port-forwarding for {} port {} (VM {}), via {}", new Object[] {targetMachine, targetPort, targetMachine, newFrontEndpoint});
        return newFrontEndpoint;
    }

    @Override
    public HostAndPort openPortForwarding(HostAndPort targetSide, Optional<Integer> optionalPublicPort, Protocol protocol, Cidr accessingCidr) {
        // FIXME Does this actually open the port forwarding? Or just record that the port is supposed to be open?
        PortForwardManager pfw = getPortForwardManager();
        PortMapping mapping;
        if (optionalPublicPort.isPresent()) {
            int publicPort = optionalPublicPort.get();
            mapping = pfw.acquirePublicPortExplicit(dockerHostname, publicPort);
        } else {
            mapping = pfw.acquirePublicPortExplicit(dockerHostname, targetSide.getPort());
        }
        if (mapping == null) {
            return HostAndPort.fromParts(dockerHostname, targetSide.getPort());
        } else {
            return HostAndPort.fromParts(dockerHostname, mapping.getPublicPort());
        }
    }

    @Override
    public boolean closePortForwarding(HostAndPort targetSide, HostAndPort publicSide, Protocol protocol) {
        // no-op; we leave the port-forwarding in place.
        // This is symmetrical with openPortForwarding, which doesn't actually open it - that just returns the existing open mapping.
        return false;
    }

    @Override
    public boolean closePortForwarding(HasNetworkAddresses machine, int targetPort, HostAndPort publicSide, Protocol protocol) {
        // no-op; we leave the port-forwarding in place.
        // This is symmetrical with openPortForwarding, which doesn't actually open it - that just returns the existing open mapping.
        return false;
    }

    public Map<Integer, Integer> getPortMappings(MachineLocation targetMachine) {
        Properties properties = new Properties();
        properties.setProperty(Constants.PROPERTY_TRUST_ALL_CERTS, Boolean.toString(true));
        properties.setProperty(Constants.PROPERTY_RELAX_HOSTNAME, Boolean.toString(true));
        ComputeServiceContext context = ContextBuilder.newBuilder("docker")
                .endpoint(dockerEndpoint)
                .credentials(dockerIdentity, dockerCredential)
                .overrides(properties)
                .modules(ImmutableSet.<Module>of(new SLF4JLoggingModule(), new SshjSshClientModule()))
                .build(ComputeServiceContext.class);

        DockerApi api = context.unwrapApi(DockerApi.class);
        String containerId = ((JcloudsSshMachineLocation) targetMachine).getJcloudsId();
        Container container = api.getContainerApi().inspectContainer(containerId);
        context.close();
        Map<Integer, Integer> portMappings = Maps.newLinkedHashMap();
        if(container.networkSettings() == null) return portMappings;
        for(Map.Entry<String, List<Map<String, String>>> entrySet : container.networkSettings().ports().entrySet()) {
            String containerPort = Iterables.get(Splitter.on("/").split(entrySet.getKey()), 0);
            String hostPort = Iterables.getOnlyElement(Iterables.transform(entrySet.getValue(),
                    new Function<Map<String, String>, String>() {
                        @Override
                        public String apply(Map<String, String> hostIpAndPort) {
                            return hostIpAndPort.get("HostPort");
                        }
                    }));
            portMappings.put(Integer.parseInt(containerPort), Integer.parseInt(hostPort));
        }
        return portMappings;
    }

    @Override
    public boolean isClient() {
        return false;
    }
}
