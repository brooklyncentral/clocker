/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package io.cloudsoft.docker.example;

import static brooklyn.util.http.HttpTool.httpClientBuilder;
import static brooklyn.util.http.HttpTool.httpGet;
import static java.lang.String.format;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.client.HttpClient;
import org.jclouds.docker.domain.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.HostAndPort;
import com.google.gson.Gson;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.container.docker.DockerCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.event.feed.http.HttpPollValue;
import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.access.PortForwardManager;
import brooklyn.location.basic.Machines;
import brooklyn.location.jclouds.JcloudsSshMachineLocation;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.guava.Maybe;
import brooklyn.util.net.Cidr;
import io.cloudsoft.networking.portforwarding.DockerPortForwarder;
import io.cloudsoft.networking.portforwarding.subnet.SubnetTierDockerImpl;
import io.cloudsoft.networking.subnet.SubnetTier;

/**
 * A live test of the {@link brooklyn.entity.container.docker.DockerCluster} entity.
 * <p/>
 * Tests that a 3 node cluster can be started on a  jclouds location and data written on one {@link brooklyn.entity.container.docker.DockerCluster}
 */
public class DockerClusterLiveTest {

    private static final Logger log = LoggerFactory.getLogger(DockerClusterLiveTest.class);

    private String provider =
              "softlayer-dallas-6";
//            "docker-boot2docker";
//            "aws-ec2:eu-west-1";
//            "named:hpcloud-compute-at";
//            "localhost";

    protected TestApplication app;
    protected Location testLocation;
    protected DockerCluster cluster;

    private DockerPortForwarder portForwarder;


    @BeforeMethod(alwaysRun = true)
    public void setup() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        testLocation = app.getManagementContext().getLocationRegistry().resolve(provider);
    }

    @AfterMethod(alwaysRun = true)
    public void shutdown() {
        Entities.destroyAll(app.getManagementContext());
    }

    /**
     * Test that a two node cluster starts up and allows access through both nodes.
     */
    @Test(groups = "Live")
    public void testStartUpConnectAndResize() throws Exception {
        try {
            portForwarder = new DockerPortForwarder(app, new PortForwardManager());

            SubnetTier subnetTier = app.createAndManageChild(EntitySpec.create(SubnetTier.class)
                    .impl(SubnetTierDockerImpl.class)
                    .configure(SubnetTier.PORT_FORWARDER, portForwarder)
                    .configure(SubnetTier.SUBNET_CIDR, Cidr.UNIVERSAL));
            cluster = subnetTier.addChild(EntitySpec.create(DockerCluster.class)
                    .configure("initialSize", 3)
                    .configure("clusterName", "DockerClusterLiveTest"));
            Assert.assertEquals(cluster.getCurrentSize().intValue(), 0);

            app.start(ImmutableList.of(testLocation));

            EntityTestUtils.assertAttributeEqualsEventually(cluster, DockerCluster.GROUP_SIZE, 3);
            Entities.dumpInfo(app);

            EntityTestUtils.assertAttributeEqualsEventually(cluster, Startable.SERVICE_UP, true);
            for (Entity dockerNode : cluster.getMembers()) {
                Assert.assertTrue(isDockerRunning(app, (DockerNode) dockerNode));
            }
            cluster.resize(1);
            EntityTestUtils.assertAttributeEqualsEventually(cluster, DockerCluster.GROUP_SIZE, 1);
            Entities.dumpInfo(app);
            EntityTestUtils.assertAttributeEqualsEventually(cluster, Startable.SERVICE_UP, true);
            for (Entity dockerNode : cluster.getMembers()) {
                Assert.assertTrue(isDockerRunning(app, (DockerNode) dockerNode));
            }


        } catch (Throwable e) {
            throw Throwables.propagate(e);
        }
    }

    protected boolean isDockerRunning(TestApplication app, DockerNode node) throws UnsupportedEncodingException, URISyntaxException {
        Maybe<JcloudsSshMachineLocation> lookup = Machines.findUniqueElement(app.getLocations(),
                JcloudsSshMachineLocation.class);
        Preconditions.checkState(lookup.isPresent(), "Must be a JcloudsSshMachineLocation available");
        JcloudsSshMachineLocation machine = lookup.get();
        //portForwarder.
        //portForwardManager.lookup(machine, node.getAttribute(DockerNode.DOCKER_PORT));
        HostAndPort hostAndPort = HostAndPort.fromParts("localhost", node.getAttribute(DockerNode.DOCKER_PORT));
        URI baseUri = new URI(format("http://%s:%s/version", hostAndPort.getHostText(), hostAndPort.getPort()));
        HttpClient client = httpClientBuilder().build();
        HttpPollValue result = httpGet(client, baseUri, ImmutableMap.<String, String>of());
        Assert.assertEquals(200, result.getResponseCode());
        Version version = new Gson().fromJson(new String(result.getContent(), "UTF-8").trim(), Version.class);
        Assert.assertEquals("amd64", version.getArch());
        Assert.assertEquals("0.9.0", version.getVersion());
        // test the new jclouds-docker location
        String locationName = "my-docker";
        LocationDefinition ldef = app.getManagementContext().getLocationRegistry().getDefinedLocationByName(locationName);
        Assert.assertEquals(locationName, ldef.getName());
        Assert.assertEquals(format("jclouds:%s:http://%s:%s", locationName, hostAndPort.getHostText(),
                node.getAttribute(DockerNode.DOCKER_PORT)), ldef.getSpec());

        return true;
    }

}
