package docker;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.container.docker.DockerNode;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.software.AbstractDockerLiveTest;
import brooklyn.event.feed.http.HttpPollValue;
import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.access.BrooklynAccessUtils;
import brooklyn.location.access.PortForwardManager;
import brooklyn.location.jclouds.JcloudsSshMachineLocation;
import brooklyn.util.net.Cidr;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.HostAndPort;
import com.google.gson.Gson;
import io.cloudsoft.networking.portforwarding.DockerPortForwarder;
import io.cloudsoft.networking.portforwarding.subnet.SubnetTierDockerImpl;
import io.cloudsoft.networking.subnet.PortForwarder;
import io.cloudsoft.networking.subnet.SubnetTier;
import org.apache.http.client.HttpClient;
import org.jclouds.docker.domain.Version;
import org.testng.annotations.Test;

import java.net.URI;

import static brooklyn.util.http.HttpTool.httpClientBuilder;
import static brooklyn.util.http.HttpTool.httpGet;
import static java.lang.String.format;
import static org.testng.Assert.assertEquals;

/**
 * @author Andrea Turli
 */
public class DockerDockerLiveTest extends AbstractDockerLiveTest {

    @Override
    protected void doTest(Location loc) throws Exception {

        URI uri = URI.create((String) loc.getAllConfig(true).get("endpoint"));
        PortForwardManager portForwardManager = new PortForwardManager();
        PortForwarder portForwarder = new DockerPortForwarder(app, portForwardManager, uri.getHost(),
                uri.getPort());

        app.createAndManageChild(EntitySpec.create(SubnetTier.class)
                .impl(SubnetTierDockerImpl.class)
                .configure(SubnetTier.PORT_FORWARDER, portForwarder)
                .configure(SubnetTier.SUBNET_CIDR, Cidr.UNIVERSAL)
                .configure(BrooklynAccessUtils.PORT_FORWARDING_MANAGER, portForwardManager));

        DockerNode docker = app.createAndManageChild(EntitySpec.create(DockerNode.class)
                .configure("docker.port", "4244+")
                .configure(BrooklynAccessUtils.PORT_FORWARDING_MANAGER, portForwardManager));

        app.start(ImmutableList.of(loc));
        Entities.dumpInfo(app);

        JcloudsSshMachineLocation jlocation = null;
        for (Location location : loc.getChildren()) {
            if(location instanceof JcloudsSshMachineLocation) {
                jlocation = ((JcloudsSshMachineLocation) location);
            }
        }
        HostAndPort hostAndPort = portForwardManager.lookup(jlocation, docker.getAttribute(DockerNode.DOCKER_PORT));
        URI baseUri = new URI(format("http://%s:%s/version", hostAndPort.getHostText(), hostAndPort.getPort()));
        HttpClient client = httpClientBuilder().build();
        HttpPollValue result = httpGet(client, baseUri, ImmutableMap.<String, String>of());
        assertEquals(200, result.getResponseCode());
        Version version = new Gson().fromJson(new String(result.getContent(), "UTF-8").trim(), Version.class);
        assertEquals("amd64", version.getArch());
        assertEquals("0.9.0", version.getVersion());
        // test the new jclouds-docker location
        String locationName = "my-docker";
        LocationDefinition ldef = app.getManagementContext().getLocationRegistry().getDefinedLocationByName(locationName);
        assertEquals(locationName, ldef.getName());
        assertEquals(format("jclouds:%s:http://%s:%s", locationName, hostAndPort.getHostText(),
                docker.getAttribute(DockerNode.DOCKER_PORT)), ldef.getSpec());
    }

    @Test(enabled = false)
    public void testDummy() {
    } // Convince testng IDE integration that this really does have test methods
}