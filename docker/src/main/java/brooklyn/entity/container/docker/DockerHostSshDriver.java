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

import static brooklyn.util.ssh.BashCommands.INSTALL_CURL;
import static brooklyn.util.ssh.BashCommands.INSTALL_WGET;
import static brooklyn.util.ssh.BashCommands.alternatives;
import static brooklyn.util.ssh.BashCommands.chainGroup;
import static brooklyn.util.ssh.BashCommands.executeCommandThenAsUserTeeOutputToFile;
import static brooklyn.util.ssh.BashCommands.ifExecutableElse0;
import static brooklyn.util.ssh.BashCommands.ifExecutableElse1;
import static brooklyn.util.ssh.BashCommands.installPackage;
import static brooklyn.util.ssh.BashCommands.sudo;
import static java.lang.String.format;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.jclouds.net.domain.IpPermission;
import org.jclouds.net.domain.IpProtocol;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.lifecycle.ScriptHelper;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.jclouds.JcloudsSshMachineLocation;
import brooklyn.location.jclouds.networking.JcloudsLocationSecurityGroupCustomizer;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.file.ArchiveUtils;
import brooklyn.util.net.Networking;
import brooklyn.util.net.Urls;
import brooklyn.util.os.Os;
import brooklyn.util.repeat.Repeater;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

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
    public String getRepository() {
        return getEntity().getAttribute(DockerHost.DOCKER_REPOSITORY);
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

        // Build an image from the base Dockerfile
        copyTemplate(dockerFile, Os.mergePaths(name, "Base" + DockerAttributes.DOCKERFILE));
        String baseImageId = getImageId("Base" + DockerAttributes.DOCKERFILE, name);
        log.info("Created base Dockerfile image with ID {}", baseImageId);

        // Update the image with the Clocker sshd Dockerfile
        copyTemplate(DockerAttributes.SSHD_DOCKERFILE, Os.mergePaths(name, "Sshd" + DockerAttributes.DOCKERFILE), true, MutableMap.of("repository", getRepository(), "imageName", name));
        String sshdImageId = getImageId("Sshd" + DockerAttributes.DOCKERFILE, name);
        log.info("Created SSHable Dockerfile image with ID {}", sshdImageId);

        return sshdImageId;
    }

    private String getImageId(String dockerfile, String name) {
        String build = format("build --rm -t %s - < %s", Os.mergePaths(getRepository(), name), Os.mergePaths(getRunDir(), name, dockerfile));
        String stdout = ((DockerHost) getEntity()).runDockerCommandTimeout(build, Duration.minutes(15));
        String prefix = Strings.getFirstWordAfter(stdout, "Successfully built");

        // Inspect the Docker image with this prefix
        String inspect = format("inspect --format={{.Id}} %s", prefix);
        String imageId = ((DockerHost) getEntity()).runDockerCommand(inspect);
        return DockerAttributes.checkId(imageId);
    }

    public String getEpelRelease() {
        return getEntity().getConfig(DockerHost.EPEL_RELEASE);
    }

    @Override
    public boolean isRunning() {
        ScriptHelper helper = newScript(CHECK_RUNNING)
                .body.append(alternatives(
                        ifExecutableElse1("boot2docker", sudo("boot2docker status")),
                        ifExecutableElse1("service", sudo("service docker status"))))
                .failOnNonZeroResultCode()
                .gatherOutput();
        helper.execute();
        return helper.getResultStdout().contains("running");
    }

    @Override
    public void stop() {
        newScript(STOPPING)
                .body.append(alternatives(
                        ifExecutableElse1("boot2docker", sudo("boot2docker down")),
                        ifExecutableElse1("service", sudo("service docker stop"))))
                .failOnNonZeroResultCode()
                .execute();
    }

    @Override
    public void preInstall() {
        resolver = Entities.newDownloader(this);
        setExpandedInstallDir(Os.mergePaths(getInstallDir(), resolver.getUnpackedDirectoryName(format("docker-%s", getVersion()))));
    }

    // TODO consider re-using `curl get.docker.io | bash` to install docker on the platform supported
    @Override
    public void install() {
        OsDetails osDetails = getMachine().getMachineDetails().getOsDetails();
        String osVersion = osDetails.getVersion();
        String arch = osDetails.getArch();
        if (!osDetails.is64bit()) {
            throw new IllegalStateException("Docker supports only 64bit OS");
        }
        if (osDetails.getName().equalsIgnoreCase("ubuntu") && osVersion.equals("12.04")) {
            List<String> commands = ImmutableList.<String>builder().add(installPackage("linux-image-generic-lts-raring"))
                    .add(installPackage("linux-headers-generic-lts-raring"))
                    .add(sudo("reboot"))
                    .build();
            executeKernelInstallation(commands);
        }
        if (osDetails.getName().equalsIgnoreCase("centos")) {
            List<String> commands = ImmutableList.<String>builder()
                    .add(sudo("yum -y --nogpgcheck upgrade kernel"))
                    .add(sudo("reboot"))
                    .build();
            executeKernelInstallation(commands);
        }

        log.info("waiting for Docker host {} to be sshable", getLocation());
        boolean isSshable = Repeater.create()
                .every(Duration.TEN_SECONDS)
                .until(new Callable<Boolean>() {
                    public Boolean call() {
                        return getLocation().isSshable();
                    }
                })
                .limitTimeTo(Duration.minutes(15)) // Because of the reboot
                .run();
        if (!isSshable) {
            throw new IllegalStateException(String.format("The entity %s is not ssh'able after reboot", entity));
        }
        log.info("Docker host {} is now sshable; continuing with setup", getLocation());

        List<String> commands = Lists.newArrayList();
        if (osDetails.getName().equalsIgnoreCase("ubuntu")) {
            commands.add(installDockerOnUbuntu());
        } else if (osDetails.getName().equalsIgnoreCase("centos")) {
            commands.add(ifExecutableElse0("yum", useYum(osVersion, arch, getEpelRelease())));
            commands.add(installPackage(ImmutableMap.of("yum", "docker-io"), null));
        } else { // Alternatively, just use the curl-able install.sh script provided at https://get.docker.io
            commands.add(chainGroup(INSTALL_CURL, "curl -s https://get.docker.io/ | sudo sh"));
        }
        newScript(INSTALLING)
                .failOnNonZeroResultCode()
                .body.append(commands)
                .execute();
    }

    private void executeKernelInstallation(List<String> commands) {
        newScript(INSTALLING + "kernel")
                .body.append(commands)
                .execute();
    }

    private String useYum(String osVersion, String arch, String epelRelease) {
            String osMajorVersion = osVersion.substring(0, osVersion.lastIndexOf("."));
        return chainGroup(
                INSTALL_WGET,
                alternatives(
                        sudo("rpm -qa | grep epel-release"),
                        sudo(format("rpm -Uvh http://dl.fedoraproject.org/pub/epel/%s/%s/epel-release-%s.noarch.rpm", osMajorVersion, arch, epelRelease)))
        );
    }

    private String installDockerOnUbuntu() {
        return chainGroup(INSTALL_CURL, "curl -s https://get.docker.io/ubuntu/ | sudo sh");
    }

    @Override
    public void customize() {
        Networking.checkPortsValid(getPortMap());

        stop();

        newScript(CUSTOMIZING)
                .failOnNonZeroResultCode()
                .body.append(
                        ifExecutableElse0("apt-get", chainGroup(
                                executeCommandThenAsUserTeeOutputToFile(format("echo 'DOCKER_OPTS=\"-H tcp://0.0.0.0:%s -H unix:///var/run/docker.sock\"'", getDockerPort()), "root", "/etc/default/docker"),
                                sudo("groupadd -f docker"),
                                sudo(format("gpasswd -a %s docker", getMachine().getUser())),
                                sudo("newgrp docker"))),
                        ifExecutableElse0("yum",
                                executeCommandThenAsUserTeeOutputToFile(format("echo 'other_args=\"--selinux-enabled -H tcp://0.0.0.0:%s -H unix:///var/run/docker.sock -e lxc\"'", getDockerPort()), "root", "/etc/sysconfig/docker")))
                .execute();

        // Configure volume mappings for the host
        Map<String, String> mapping = MutableMap.of();
        Map<String, String> volumes = getEntity().getConfig(DockerHost.DOCKER_HOST_VOLUME_MAPPING);
        if (volumes != null) {
            for (String source : volumes.keySet()) {
                if (Urls.isUrlWithProtocol(source)) {
                    String path = deployArchive(source);
                    mapping.put(path, volumes.get(source));
                } else {
                    mapping.put(source, volumes.get(source));
                }
            }
        }
        getEntity().setAttribute(DockerHost.DOCKER_HOST_VOLUME_MAPPING, mapping); 
    }

    @Override
    public String deployArchive(String url) {
        String volumeId = Identifiers.makeIdFromHash(url.hashCode());
        String path = Os.mergePaths(getRunDir(), volumeId);
        ArchiveUtils.deploy(url, getMachine(), path);
        return path;
    }

    @Override
    public void configureSecurityGroups() {
        String securityGroup = getEntity().getConfig(DockerInfrastructure.SECURITY_GROUP);
        if (Strings.isBlank(securityGroup)) {
            if (!(getLocation() instanceof JcloudsSshMachineLocation)) {
                log.info("{} not running in a JcloudsSshMachineLocation, not configuring extra security groups", entity);
                return;
            }
            JcloudsSshMachineLocation location = (JcloudsSshMachineLocation) getLocation();
            Collection<IpPermission> permissions = getIpPermissions();
            log.debug("Applying custom security groups to {}: {}", location, permissions);
            JcloudsLocationSecurityGroupCustomizer.getInstance(getEntity().getApplicationId())
                    .addPermissionsToLocation(location, permissions);
        }
    }

    /**
     * @return Extra IP permissions to be configured on this entity's location.
     */
    protected Collection<IpPermission> getIpPermissions() {
        IpPermission dockerPort = IpPermission.builder()
                .ipProtocol(IpProtocol.TCP)
                .fromPort(getEntity().getAttribute(DockerHost.DOCKER_PORT))
                .toPort(getEntity().getAttribute(DockerHost.DOCKER_PORT))
                .cidrBlock(JcloudsLocationSecurityGroupCustomizer.getInstance(getEntity()).getBrooklynCidrBlock())
                .build();
        IpPermission dockerSslPort = IpPermission.builder()
                .ipProtocol(IpProtocol.TCP)
                .fromPort(getEntity().getAttribute(DockerHost.DOCKER_SSL_PORT))
                .toPort(getEntity().getAttribute(DockerHost.DOCKER_SSL_PORT))
                .cidrBlock(JcloudsLocationSecurityGroupCustomizer.getInstance(getEntity()).getBrooklynCidrBlock())
                .build();
        IpPermission dockerPortForwarding = IpPermission.builder()
                .ipProtocol(IpProtocol.TCP)
                .fromPort(49000)
                .toPort(49900)
                .cidrBlock(JcloudsLocationSecurityGroupCustomizer.getInstance(getEntity()).getBrooklynCidrBlock())
                .build();
        return ImmutableList.of(dockerPort, dockerSslPort, dockerPortForwarding);
    }

    public String getPidFile() { return "/var/run/docker.pid"; }

    @Override
    public void launch() {
        newScript(LAUNCHING)
                .body.append(alternatives(
                        ifExecutableElse1("boot2docker", sudo("boot2docker up")),
                        ifExecutableElse1("service", sudo("service docker start"))))
                .failOnNonZeroResultCode()
                .uniqueSshConnection()
                .execute();
    }

}
