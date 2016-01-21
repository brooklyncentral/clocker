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
package brooklyn.entity.container.docker.registry;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.HostAndPort;

import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.feed.ConfigToAttributes;
import org.apache.brooklyn.core.location.access.BrooklynAccessUtils;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.feed.http.HttpFeed;
import org.apache.brooklyn.feed.http.HttpPollConfig;
import org.apache.brooklyn.feed.http.HttpValueFunctions;
import org.apache.brooklyn.feed.http.JsonFunctions;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.internal.ssh.SshTool;
import org.apache.brooklyn.util.guava.Functionals;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.ssh.BashCommands;
import org.apache.brooklyn.util.time.Duration;

import brooklyn.entity.container.DockerAttributes;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.entity.container.docker.application.VanillaDockerApplicationImpl;

public class DockerRegistryImpl extends VanillaDockerApplicationImpl implements DockerRegistry {

    private transient HttpFeed httpFeed;

    public static final String CREATE_CERTS_SCRIPT_NAME = "docker-registry-create-certs.sh";
    public static final String SCRIPT_LOCATION = "classpath://brooklyn/entity/container/docker/registry/" + CREATE_CERTS_SCRIPT_NAME;
    public static final String DOCKER_REGISTRY_LOGO = "classpath://docker-registry-logo.png";

    private String getSSHMachineInstallDir() {
        return config().get(DockerRegistry.DOCKER_HOST).sensors().get(SoftwareProcess.INSTALL_DIR);
    }

    @Override
    public String getIconUrl() {
        return DOCKER_REGISTRY_LOGO;
    }

    @Override
    public void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();

        HostAndPort hostAndPort = BrooklynAccessUtils.getBrooklynAccessibleAddress(this, sensors().get(DOCKER_REGISTRY_PORT));
        sensors().set(Attributes.MAIN_URI, URI.create("https://" + hostAndPort + "/v2"));

        httpFeed = HttpFeed.builder()
                .entity(this)
                .period(Duration.seconds(3))
                .baseUri(getAttribute(Attributes.MAIN_URI))
                .poll(new HttpPollConfig<Boolean>(Attributes.SERVICE_UP)
                        .onSuccess(Functions.constant(true))
                        .onFailureOrException(Functions.constant(false)))
                .poll(new HttpPollConfig<List<String>>(DOCKER_REGISTRY_CATALOG)
                        .suburl("/_catalog")
                        .onSuccess(Functionals.chain(
                                HttpValueFunctions.jsonContents(),
                                JsonFunctions.walk("repositories"),
                                JsonFunctions.forEach(JsonFunctions.cast(String.class))))
                        .onFailureOrException(Functions.constant(Collections.<String>emptyList())))
                .build();
    }

    @Override
    public void disconnectSensors() {
        if (httpFeed != null) {
            httpFeed.stop();
        }
        disconnectServiceUpIsRunning();
        super.disconnectSensors();
    }

    @Override
    public void init() {
        super.init();

        ConfigToAttributes.apply(this, DOCKER_HOST);
        ConfigToAttributes.apply(this, DOCKER_REGISTRY_PORT);

        DockerHost host = (DockerHost) sensors().get(DOCKER_HOST);
        String installDir = host.sensors().get(SoftwareProcess.INSTALL_DIR);
        SshMachineLocation sshMachine = host.getDynamicLocation().getMachine();
        String sshMachineInstallDir = getSSHMachineInstallDir();

        config().set(DockerAttributes.DOCKER_PORT_BINDINGS, MutableMap.of(sensors().get(DOCKER_REGISTRY_PORT), 5000));
        config().set(DockerAttributes.DOCKER_HOST_VOLUME_MAPPING, MutableMap.of(Os.mergePaths(installDir, "certs"), "/certs"));

        sshMachine.installTo(ImmutableMap.of(SshTool.PROP_PERMISSIONS.getName(), "0755"), SCRIPT_LOCATION, Os.mergePaths(sshMachineInstallDir, CREATE_CERTS_SCRIPT_NAME));
        sshMachine.installTo(config().get(DockerInfrastructure.DOCKER_CA_CERTIFICATE_PATH), Os.mergePaths(sshMachineInstallDir, "ca-cert.pem"));
        sshMachine.installTo(config().get(DockerInfrastructure.DOCKER_CA_KEY_PATH), Os.mergePaths(sshMachineInstallDir, "ca-key.pem"));

        int result = sshMachine.execCommands("installCerts",
                ImmutableList.of(
                                BashCommands.sudo(String.format("%s %s %s",
                                                Os.mergePaths(sshMachineInstallDir, CREATE_CERTS_SCRIPT_NAME),
                                                host.sensors().get(Attributes.ADDRESS),
                                                sshMachineInstallDir)
                                )));
        if (result != 0) {
            throw new IllegalStateException("Could not create certificates for docker registry");
        }
    }
}
