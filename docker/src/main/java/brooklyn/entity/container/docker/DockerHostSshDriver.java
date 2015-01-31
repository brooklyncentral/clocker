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

import static brooklyn.util.ssh.BashCommands.*;
import static java.lang.String.format;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.jclouds.net.domain.IpPermission;
import org.jclouds.net.domain.IpProtocol;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.lifecycle.ScriptHelper;
import brooklyn.entity.container.DockerUtils;
import brooklyn.entity.container.sdn.SdnAgent;
import brooklyn.entity.container.sdn.weave.WeaveNetwork;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.jclouds.JcloudsSshMachineLocation;
import brooklyn.location.jclouds.networking.JcloudsLocationSecurityGroupCustomizer;
import brooklyn.management.Task;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.file.ArchiveUtils;
import brooklyn.util.net.Cidr;
import brooklyn.util.net.Networking;
import brooklyn.util.net.Urls;
import brooklyn.util.os.Os;
import brooklyn.util.repeat.Repeater;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.TaskBuilder;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

public class DockerHostSshDriver extends AbstractSoftwareProcessSshDriver implements DockerHostDriver {

    public DockerHostSshDriver(DockerHostImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    protected Map<String, Integer> getPortMap() {
        Map<String, Integer> ports = MutableMap.of();
        ports.put("dockerPort", getDockerPort());
        if (getEntity().getConfig(DockerInfrastructure.SDN_ENABLE)) {
            // XXX make generic
            // Best guess at available port, as Weave is started _after_ the DockerHost
            Integer weavePort = getEntity()
                    .getAttribute(DockerHost.DOCKER_INFRASTRUCTURE)
                    .getAttribute(DockerInfrastructure.SDN_PROVIDER)
                    .getConfig(WeaveNetwork.WEAVE_PORT);
            if (weavePort != null) ports.put("weavePort", weavePort);
        }
        return ports;
    }

    @Override
    public Set<Integer> getPortsUsed() {
        return ImmutableSet.<Integer> builder()
                .addAll(super.getPortsUsed())
                .addAll(getPortMap().values())
                .build();
    }

    @Override
    public Integer getDockerPort() {
        return getEntity().getAttribute(DockerHost.DOCKER_SSL_PORT);
    }

    @Override
    public String getRepository() {
        return getEntity().getAttribute(DockerHost.DOCKER_REPOSITORY);
    }

    /** {@inheritDoc} */
    @Override
    public String buildImage(String dockerFile, String name) {
        if (ArchiveUtils.ArchiveType.UNKNOWN != ArchiveUtils.ArchiveType.of(dockerFile) || Urls.isDirectory(dockerFile)) {
            ArchiveUtils.deploy(dockerFile, getMachine(), Os.mergePaths(getRunDir(), name));
            String baseImageId = buildDockerfileDirectory(name);
            log.info("Created base Dockerfile image with ID {}", baseImageId);
        } else {
            ProcessTaskWrapper<Integer> task = SshEffectorTasks.ssh(format("mkdir -p %s", Os.mergePaths(getRunDir(), name)))
                    .machine(getMachine())
                    .newTask();
            DynamicTasks.queueIfPossible(task).executionContext(getEntity()).orSubmitAndBlock();
            int result = task.get();
            if (result != 0) throw new IllegalStateException("Error creating image directory: " + name);

            // Build an image from the base Dockerfile
            copyTemplate(dockerFile, Os.mergePaths(name, "Base" + DockerUtils.DOCKERFILE),
                    false, getExtraTemplateSubstitutions(name));
            String baseImageId = buildDockerfile("Base" + DockerUtils.DOCKERFILE, name);
            log.info("Created base Dockerfile image with ID {}", baseImageId);
        }

        // Update the image with the Clocker sshd Dockerfile
        copyTemplate(DockerUtils.SSHD_DOCKERFILE, Os.mergePaths(name, "Sshd" + DockerUtils.DOCKERFILE),
                false, getExtraTemplateSubstitutions(name));
        String sshdImageId = buildDockerfile("Sshd" + DockerUtils.DOCKERFILE, name);
        log.info("Created SSHable Dockerfile image with ID {}", sshdImageId);

        return sshdImageId;
    }

    private Map<String, Object> getExtraTemplateSubstitutions(String imageName) {
        Map<String, Object> templateSubstitutions = MutableMap.<String, Object> of("repository", getRepository(), "imageName", imageName);
        DockerHost host = (DockerHost) getEntity();
        templateSubstitutions.putAll(host.getInfrastructure().getConfig(DockerInfrastructure.DOCKERFILE_SUBSTITUTIONS));
        return templateSubstitutions;
    }

    private String buildDockerfileDirectory(String name) {
        String build = format("build --rm -t %s %s", Os.mergePaths(getRepository(), name), Os.mergePaths(getRunDir(), name));
        String stdout = ((DockerHost) getEntity()).runDockerCommandTimeout(build, Duration.minutes(20));
        String prefix = Strings.getFirstWordAfter(stdout, "Successfully built");

        return getImageId(prefix, name);
    }

    private String buildDockerfile(String dockerfile, String name) {
        String build = format("build --rm -t %s - < %s", Os.mergePaths(getRepository(), name), Os.mergePaths(getRunDir(), name, dockerfile));
        String stdout = ((DockerHost) getEntity()).runDockerCommandTimeout(build, Duration.minutes(20));
        String prefix = Strings.getFirstWordAfter(stdout, "Successfully built");

        return getImageId(prefix, name);
    }

    // Inspect the Docker image with this prefix
    private String getImageId(String prefix, String name) {
        String inspect = format("inspect --format={{.Id}} %s", prefix);
        String imageId = ((DockerHost) getEntity()).runDockerCommand(inspect);
        return DockerUtils.checkId(imageId);
    }

    public String getEpelRelease() {
        return getEntity().getConfig(DockerHost.EPEL_RELEASE);
    }

    @Override
    public boolean isRunning() {
        ScriptHelper helper = newScript(CHECK_RUNNING)
                .body.append(alternatives(
                        ifExecutableElse1("boot2docker", "boot2docker status"),
                        ifExecutableElse1("service", sudo("service docker status"))))
                // otherwise Brooklyn appends 'check-running' and the method always returns true.
                .noExtraOutput()
                .gatherOutput();
        helper.execute();
        return helper.getResultStdout().contains("running");
    }

    @Override
    public void stop() {
        newScript(STOPPING).body.append(alternatives(
                ifExecutableElse1("boot2docker", "boot2docker down"),
                ifExecutableElse1("service", sudo("service docker stop"))))
                .failOnNonZeroResultCode()
                .execute();
    }

    @Override
    public void preInstall() {
        resolver = Entities.newDownloader(this);
        setExpandedInstallDir(Os.mergePaths(getInstallDir(), resolver.getUnpackedDirectoryName(format("docker-%s", getVersion()))));
    }

    @Override
    public void install() {
        OsDetails osDetails = getMachine().getMachineDetails().getOsDetails();
        String osVersion = osDetails.getVersion();
        String arch = osDetails.getArch();
        if (!osDetails.is64bit()) { throw new IllegalStateException("Docker supports only 64bit OS"); }
        if (osDetails.isLinux()) {
            if (osDetails.getName().equalsIgnoreCase("ubuntu") && osVersion.equals("12.04")) {
                List<String> commands = ImmutableList.<String> builder().add(installPackage("linux-image-generic-lts-raring"))
                        .add(installPackage("linux-headers-generic-lts-raring"))
                        .add(sudo("reboot"))
                        .build();
                executeKernelInstallation(commands);
            }
            if (osDetails.getName().equalsIgnoreCase("centos")) {
                List<String> commands = ImmutableList.<String> builder()
                        .add(sudo("yum -y --nogpgcheck upgrade kernel"))
                        .add(sudo("reboot"))
                        .build();
                executeKernelInstallation(commands);
            }
        } else if (osDetails.isMac()) {
            newScript(LAUNCHING).body.append(
                            alternatives(
                                    ifExecutableElse1("boot2docker", "boot2docker status || boot2docker init"),
                                    fail("Mac OSX install requires Boot2Docker preinstalled", 1)))
                    .failOnNonZeroResultCode()
                    .execute();
            return;
        } else if (osDetails.isWindows()) {
            throw new IllegalStateException("Windows operating system not yet supported by Docker");
        } else {
            throw new IllegalStateException("Unknown operating system: " + osDetails);
        }

        // Wait until the Docker host is SSHable after the reboot
        // Don't check immediately; it could take a few seconds for rebooting to make the machine not ssh'able;
        // must not accidentally think it's rebooted before we've actually rebooted!
        Stopwatch stopwatchForReboot = Stopwatch.createStarted();
        Time.sleep(Duration.seconds(30));

        Task<Boolean> sshable = TaskBuilder.<Boolean> builder()
                .name("Waiting until host is SSHable")
                .body(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        return Repeater.create()
                                .every(Duration.TEN_SECONDS)
                                .until(new Callable<Boolean>() {
                                    public Boolean call() {
                                        return getLocation().isSshable();
                                    }
                                })
                                .limitTimeTo(Duration.minutes(15)) // Because of the reboot
                                .run();
                    }
                })
                .build();
        Boolean result = DynamicTasks.queueIfPossible(sshable)
                .orSubmitAndBlock()
                .andWaitForSuccess();
        if (!result) {
            throw new IllegalStateException(String.format("The entity %s is not sshable after reboot (waited %s)", 
                    entity, Time.makeTimeStringRounded(stopwatchForReboot)));
        }

        List<String> commands = Lists.newArrayList();
        if (osDetails.getName().equalsIgnoreCase("ubuntu")) {
            commands.add(installDockerOnUbuntu());
        } else if (osDetails.getName().equalsIgnoreCase("centos")) {
            commands.add(ifExecutableElse0("yum", useYum(osVersion, arch, getEpelRelease())));
            commands.add(installPackage(ImmutableMap.of("yum", "docker-io"), null));
        } else {
            commands.add(installDockerFallback());
        }
        newScript(INSTALLING)
                .failOnNonZeroResultCode()
                .body.append(commands)
                .execute();
    }

    private void executeKernelInstallation(List<String> commands) {
        newScript(INSTALLING + "kernel").body.append(commands)
                .execute();
    }

    private String useYum(String osVersion, String arch, String epelRelease) {
        String osMajorVersion = osVersion.substring(0, osVersion.lastIndexOf("."));
        return chainGroup(
                INSTALL_WGET,
                alternatives(
                        sudo("rpm -qa | grep epel-release"),
                        sudo(format("rpm -Uvh http://dl.fedoraproject.org/pub/epel/%s/%s/epel-release-%s.noarch.rpm", osMajorVersion, arch, epelRelease))));
    }

    private String installDockerOnUbuntu() {
        String version = getVersion();
        if (version.matches("^[0-9]+\\.[0-9]+$")) {
            version += ".0"; // Append minor version
        }
        if (log.isDebugEnabled()) log.debug("Installing Docker version {} on Ubuntu", version);
        return chainGroup(
                INSTALL_CURL,
                installPackage("apt-transport-https"),
                "echo 'deb https://get.docker.com/ubuntu docker main' | " + sudo("tee -a /etc/apt/sources.list.d/docker.list"),
                sudo("apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys 36A1D7869245C8950F966E92D8576A8BA88D21E9"),
                installPackage("lxc-docker-" + version));
    }

    /**
     * Uses the curl-able install.sh script provided at {@code get.docker.com}.
     * This will install the latest version, which may be incompatible with the
     * jclouds driver.
     */
    private String installDockerFallback() {
        return chainGroup(INSTALL_CURL, "curl -s https://get.docker.com/ | " + sudo("sh"));
    }

    @Override
    public void customize() {
        if (isRunning()) {
            log.info("Stopping running Docker instance at {} before customising", getMachine());
            stop();
        }

        Networking.checkPortsValid(getPortMap());

        newScript(CUSTOMIZING)
                .failOnNonZeroResultCode()
                .body.append(
                        ifExecutableElse0("apt-get", chainGroup(
                                format("echo 'DOCKER_OPTS=\"-H tcp://0.0.0.0:%d -H unix:///var/run/docker.sock --tls --tlscert=%s/cert.pem --tlskey=%s/key.pem\"' | ", getDockerPort(), getRunDir(), getRunDir()) + sudo("tee -a /etc/default/docker"),
                                sudo("groupadd -f docker"),
                                sudo(format("gpasswd -a %s docker", getMachine().getUser())),
                                sudo("newgrp docker"))),
                        ifExecutableElse0("yum",
                                format("echo 'other_args=\"--selinux-enabled -H tcp://0.0.0.0:%d -H unix:///var/run/docker.sock -e lxc --tls --tlscert=%s/cert.pem --tlskey=%s/key.pem\"' | ", getDockerPort(), getRunDir(), getRunDir()) + sudo("tee -a /etc/sysconfig/docker")))
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
            // TODO check GCE compatibility?
            JcloudsSshMachineLocation location = (JcloudsSshMachineLocation) getLocation();
            JcloudsLocationSecurityGroupCustomizer customizer = JcloudsLocationSecurityGroupCustomizer.getInstance(getEntity().getApplicationId());
            Collection<IpPermission> permissions = getIpPermissions(customizer);
            log.debug("Applying custom security groups to {}: {}", location, permissions);
            customizer.addPermissionsToLocation(location, permissions);
        }
    }

    /**
     * @return Extra IP permissions to be configured on this entity's location.
     */
    protected Collection<IpPermission> getIpPermissions(JcloudsLocationSecurityGroupCustomizer customizer) {
        IpPermission dockerPort = IpPermission.builder()
                .ipProtocol(IpProtocol.TCP)
                .fromPort(getEntity().getAttribute(DockerHost.DOCKER_PORT))
                .toPort(getEntity().getAttribute(DockerHost.DOCKER_PORT))
                .cidrBlock(customizer.getBrooklynCidrBlock())
                .build();
        IpPermission dockerSslPort = IpPermission.builder()
                .ipProtocol(IpProtocol.TCP)
                .fromPort(getEntity().getAttribute(DockerHost.DOCKER_SSL_PORT))
                .toPort(getEntity().getAttribute(DockerHost.DOCKER_SSL_PORT))
                .cidrBlock(customizer.getBrooklynCidrBlock())
                .build();
        IpPermission dockerPortForwarding = IpPermission.builder()
                .ipProtocol(IpProtocol.TCP)
                .fromPort(49000)
                .toPort(49900)
                .cidrBlock(Cidr.UNIVERSAL.toString())
                .build();
        List<IpPermission> permissions = MutableList.of(dockerPort, dockerSslPort, dockerPortForwarding);

        if (getEntity().getConfig(DockerInfrastructure.SDN_ENABLE)) {
            SdnAgent agent = getEntity().getAttribute(SdnAgent.SDN_AGENT);
            Collection<IpPermission> sdnPermissions = agent.getIpPermissions();
            permissions.addAll(sdnPermissions);
        }

        return permissions;
    }

    @Override
    public void launch() {
        newScript(LAUNCHING).body.append(alternatives(
                ifExecutableElse1("boot2docker", "boot2docker up"),
                ifExecutableElse1("service", sudo("service docker start"))))
                .failOnNonZeroResultCode()
                .uniqueSshConnection()
                .execute();
    }

    public String getPidFile() {
        return "/var/run/docker.pid";
    }

    @Override
    public Map<String, String> getShellEnvironment() {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.<String, String> builder()
                .putAll(super.getShellEnvironment());
        if (getMachine().getMachineDetails().getOsDetails().isMac()) {
            builder.put("DOCKER_HOST", format("tcp://%s:%d", getSubnetAddress(), getDockerPort()));
        }
        return builder.build();
    }

}
