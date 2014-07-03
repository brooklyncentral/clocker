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

import static brooklyn.util.ssh.BashCommands.INSTALL_WGET;
import static brooklyn.util.ssh.BashCommands.chainGroup;
import static brooklyn.util.ssh.BashCommands.ifExecutableElse0;
import static brooklyn.util.ssh.BashCommands.installPackage;
import static brooklyn.util.ssh.BashCommands.sudo;
import static java.lang.String.format;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Uninterruptibles;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.lifecycle.ScriptHelper;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;
import brooklyn.util.os.Os;
import brooklyn.util.repeat.Repeater;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;

public class DockerHostSshDriver extends AbstractSoftwareProcessSshDriver implements DockerHostDriver {

    public static final String DOCKERFILE = "Dockerfile";

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

    /** {@inheritDoc} */
    @Override
    public String buildImage(String dockerFile, String name) {
        ProcessTaskWrapper<Integer> task = SshEffectorTasks.ssh(format("mkdir -p %s", Os.mergePaths(getRunDir(), name)))
                .machine(getMachine())
                .newTask();
        DynamicTasks.queueIfPossible(task).executionContext(getEntity()).orSubmitAndBlock();
        int result = task.get();
        if (result != 0) throw new IllegalStateException("Error creating image directory: " + name);

        copyTemplate(dockerFile, Os.mergePaths(name, DOCKERFILE));

        // Build an image from the Dockerfile
        String build = format("docker build --rm -t %s - < %s", Os.mergePaths("brooklyn", name), Os.mergePaths(getRunDir(), name, DOCKERFILE));
        String stdout = ((DockerHost) getEntity()).execCommandTimeout(sudo(build), Duration.minutes(15));
        String prefix = Strings.getFirstWordAfter(stdout, "Successfully built");

        // Inspect the Docker image with this prefix
        String inspect = format("inspect --format={{.Id}} %s", prefix);
        String imageId = ((DockerHost) getEntity()).runDockerCommand(inspect);

        // Parse and return the Image ID
        imageId = Strings.trim(imageId).toLowerCase(Locale.ENGLISH);
        if (imageId.length() == 64 && CharMatcher.anyOf("0123456789abcdef").matchesAllOf(imageId)) {
            return imageId;
        } else {
            throw new IllegalStateException("Invalid image ID returned: " + imageId);
        }
    }

    public String getEpelRelease() {
        return getEntity().getConfig(DockerHost.EPEL_RELEASE);
    }

    @Override
    public boolean isRunning() {
        final ScriptHelper helper = newScript(CHECK_RUNNING)
                .body.append(sudo("service docker status"))
                .failOnNonZeroResultCode()
                .gatherOutput();
        Uninterruptibles.sleepUninterruptibly(5, TimeUnit.SECONDS);
        return Repeater.create()
                .every(Duration.ONE_SECOND)
                .until(new Callable<Boolean>() {
                    public Boolean call() {
                        helper.execute();
                        return helper.getResultStdout().contains("running");
                    }})
                .limitTimeTo(Duration.ONE_MINUTE)
                .rethrowExceptionImmediately()
                .run();
    }

    @Override
    public void stop() {
        newScript(STOPPING)
                .body.append(sudo("service docker stop"))
                .failOnNonZeroResultCode()
                .execute();
    }

    // TODO consider re-using `curl get.docker.io | bash` to install docker on the platform supported
    @Override
    public void install() {
        DownloadResolver resolver = Entities.newDownloader(this);
        setExpandedInstallDir(getInstallDir()+"/"+resolver.getUnpackedDirectoryName(format("docker-%s", getVersion())));

        OsDetails osDetails = getMachine().getMachineDetails().getOsDetails();
        String osVersion = osDetails.getVersion();
        String arch = osDetails.getArch();
        if (!osDetails.is64bit()) {
            throw new IllegalStateException("Docker supports only 64bit OS");
        }
        if (osDetails.getName().equalsIgnoreCase("ubuntu") && osVersion.equals("12.04")) {
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
                .every(Duration.TEN_SECONDS)
                .until(new Callable<Boolean>() {
                    public Boolean call() {
                        return getLocation().isSshable();
                    }})
                .limitTimeTo(Duration.minutes(15)) // Because of the reboot
                .run();
        if (!isSshable) {
            throw new IllegalStateException(String.format("The entity %s is not ssh'able after reboot", entity));
        }
        log.info("Docker host {} is now sshable; continuing with setup", getLocation());

        List<String> commands = ImmutableList.<String> builder()
                .add(ifExecutableElse0("yum", useYum(osVersion, arch, getEpelRelease())))
                .add(ifExecutableElse0("apt-get", useApt()))
                .add(installPackage(ImmutableMap.of("yum", "docker-io", "apt", "lxc-docker"), null))
                .build();

        newScript(INSTALLING)
                .failOnNonZeroResultCode()
                .body.append(commands)
                .execute();
    }

    private String useYum(String osVersion, String arch, String epelRelease) {
        String osMajorVersion = osVersion.substring(0, osVersion.lastIndexOf("."));
        return chainGroup(
                INSTALL_WGET,
                sudo(BashCommands.alternatives(sudo("rpm -qa | grep epel-release"),
                        sudo(format(
                                "rpm -Uvh http://dl.fedoraproject.org/pub/epel/%s/%s/epel-release-%s.noarch.rpm",
                                osMajorVersion, arch, epelRelease))
                ))
        );
    }

    private String useApt() {
        return chainGroup(
                INSTALL_WGET,
                sudo("apt-key adv --keyserver keyserver.ubuntu.com --recv-keys 36A1D7869245C8950F966E92D8576A8BA88D21E9"),
                "echo \"deb http://get.docker.io/ubuntu docker main\" | sudo tee -a /etc/apt/sources.list.d/docker.list"
        );
    }

    @Override
    public void customize() {
        log.debug("Customizing {}", entity);
        Networking.checkPortsValid(getPortMap());
        List<String> commands = ImmutableList.<String> builder()
                .add(sudo("service docker stop"))
                .add(ifExecutableElse0("apt-get", format("echo 'DOCKER_OPTS=\"-H tcp://0.0.0.0:%s -H unix:///var/run/docker.sock\"' | sudo tee -a /etc/default/docker", getDockerPort())))
                .add(ifExecutableElse0("yum", format("echo 'other_args=\"--selinux-enabled -H tcp://0.0.0.0:%s -H unix:///var/run/docker.sock -e lxc\"' | sudo tee /etc/sysconfig/docker", getDockerPort())))
                .build();

        newScript(CUSTOMIZING)
                .failOnNonZeroResultCode()
                .body.append(commands)
                .execute();
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
