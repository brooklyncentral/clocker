/*
 * Copyright 2013 by Cloudsoft Corp.
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
package docker;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.software.OsTasks;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;

import static brooklyn.util.ssh.BashCommands.installPackage;
import static brooklyn.util.ssh.BashCommands.sudo;
import static java.lang.String.format;

public class DockerSshDriver extends AbstractSoftwareProcessSshDriver implements DockerDriver {

    public DockerSshDriver(DockerNodeImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    protected Map<String, Integer> getPortMap() {
        return MutableMap.of("dockerPort", getDockerPort());
    }

    @Override
    public Integer getDockerPort() {
        return getEntity().getAttribute(DockerNode.DOCKER_PORT);
    }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of("usePidFile", getPidFile()), CHECK_RUNNING).body.append("true").execute() == 0;
    }

    @Override
    public void stop() {
        newScript(MutableMap.of("usePidFile", getPidFile()), STOPPING).body.append("true").execute();
        /*
        newScript(STOPPING)
                .body.append(sudo("service docker stop"))
                .execute();
        */
    }

    @Override
    public void install() {
        DownloadResolver resolver = Entities.newDownloader(this);
        setExpandedInstallDir(getInstallDir()+"/"+resolver.getUnpackedDirectoryName(format("docker-%s", getVersion())));

        OsDetails osDetails = Entities.submit(entity, OsTasks.getOsDetails(entity)).getUnchecked();
        String osMajorVersion = osDetails.getVersion();
        String osName = osDetails.getName();
        if(osName.equalsIgnoreCase("ubuntu") && osMajorVersion.equals("12.04")) {
            List<String> commands = ImmutableList.<String> builder().add(installPackage("linux-image-generic-lts-raring"))
                           .add(installPackage("linux-headers-generic-lts-raring"))
                           .add(sudo("reboot"))
                           .build();
            newScript(INSTALLING+"kernel")
                    .failOnNonZeroResultCode()
                    .body.append(commands)
                    .execute();
        }

        int retriesRemaining = 10;
        boolean isSshable;
        do {
            isSshable = this.getLocation().isSshable();
        } while (!isSshable && --retriesRemaining > 0);

        if(!isSshable) {
            throw new IllegalStateException("The entity is not ssh'able after reboot");
        }

        List<String> commands = ImmutableList.<String> builder()
                .add(sudo("apt-key adv --keyserver keyserver.ubuntu.com --recv-keys 36A1D7869245C8950F966E92D8576A8BA88D21E9"))
                .add("echo \"deb http://get.docker.io/ubuntu docker main\" | sudo tee -a /etc/apt/sources.list.d/docker.list")
                .add(installPackage("lxc-docker"))
                .build();

        newScript(INSTALLING)
                .failOnNonZeroResultCode()
                .body.append(commands)
                .execute();        
    }

    @Override
    public void customize() {
        log.debug("Customizing {}", entity);
        Networking.checkPortsValid(getPortMap());
        List<String> commands = ImmutableList.<String> builder()
                .add(sudo("service docker stop"))
                .add(format("echo 'DOCKER_OPTS=\"-H tcp://0.0.0.0:%s\"' | sudo tee -a /etc/default/docker", getDockerPort()))
                .build();

        newScript(CUSTOMIZING)
                .body.append(commands).execute();
    }

    public String getPidFile() { return "/var/run/docker.pid"; }

    @Override
    public void launch() {
        newScript(MutableMap.of("usePidFile", getPidFile()), LAUNCHING)
                .body.append(sudo("service docker start"))
                .execute();
    }

}
