/*
 * Copyright 2014 by Cloudsoft Corporation Limited
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
package brooklyn.entity.container.docker;

import static brooklyn.util.ssh.BashCommands.installPackage;
import static brooklyn.util.ssh.BashCommands.sudo;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.ImmutableList;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.lifecycle.ScriptHelper;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.internal.Repeater;
import brooklyn.util.net.Networking;

public class DockerHostSshDriver extends AbstractSoftwareProcessSshDriver implements DockerHostDriver {

    public DockerHostSshDriver(DockerHostImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    protected Map<String, Integer> getPortMap() {
        return MutableMap.of("dockerPort", getDockerPort());
    }

    @Override
    public Integer getDockerPort() {
        return getEntity().getAttribute(DockerHost.DOCKER_PORT);
    }

    @Override
    public boolean isRunning() {
        ScriptHelper helper = newScript(CHECK_RUNNING)
                .body.append(sudo("service docker status"))
                .failOnNonZeroResultCode()
                .gatherOutput();
        int result = helper.execute();
        if (result != 0) {
            throw new IllegalStateException("Error listing classpath files: " + helper.getResultStderr());
        }
        return helper.getResultStdout().contains("running");
    }

    @Override
    public void stop() {
        newScript(STOPPING)
                .body.append(sudo("service docker stop"))
                .failOnNonZeroResultCode()
                .execute();
    }

    @Override
    public void install() {
        DownloadResolver resolver = Entities.newDownloader(this);
        setExpandedInstallDir(getInstallDir()+"/"+resolver.getUnpackedDirectoryName(format("docker-%s", getVersion())));

        OsDetails osDetails = getMachine().getMachineDetails().getOsDetails();
        String osMajorVersion = osDetails.getVersion();
        String osName = osDetails.getName();
        /*
        if(!osDetails.is64bit()) {
            throw new IllegalStateException("Docker supports only 64bit OS");
        }
        */
        if(osName.equalsIgnoreCase("ubuntu") && osMajorVersion.equals("12.04")) {
            List<String> commands = ImmutableList.<String> builder().add(installPackage("linux-image-generic-lts-raring"))
                           .add(installPackage("linux-headers-generic-lts-raring"))
                           .add(sudo("reboot"))
                           .build();
            newScript(INSTALLING+"kernel")
                    .body.append(commands)
                    .execute();
        }
        log.info("waiting for Docker host {} to be sshable", getLocation());
        boolean isSshable = Repeater.create()
                .repeat()
                .every(1,SECONDS)
                .until(new Callable<Boolean>() {
                    public Boolean call() {
                        return getLocation().isSshable();
                    }})
                .limitTimeTo(30, TimeUnit.MINUTES)
                .run();
        if(!isSshable) {
            throw new IllegalStateException(String.format("The entity %s is not ssh'able after reboot", entity));
        }
        log.info("Docker host {} is now sshable; continuing with setup", getLocation());

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
        newScript(LAUNCHING)
                .body.append(sudo("service docker start"))
                .failOnNonZeroResultCode()
                .execute();
    }

}
