/*
 * Copyright 2014-2015 by Cloudsoft Corporation Limited
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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static org.apache.brooklyn.util.ssh.BashCommands.INSTALL_CURL;
import static org.apache.brooklyn.util.ssh.BashCommands.alternatives;
import static org.apache.brooklyn.util.ssh.BashCommands.chain;
import static org.apache.brooklyn.util.ssh.BashCommands.chainGroup;
import static org.apache.brooklyn.util.ssh.BashCommands.fail;
import static org.apache.brooklyn.util.ssh.BashCommands.ifExecutableElse0;
import static org.apache.brooklyn.util.ssh.BashCommands.ifExecutableElse1;
import static org.apache.brooklyn.util.ssh.BashCommands.installPackage;
import static org.apache.brooklyn.util.ssh.BashCommands.sudo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import org.apache.brooklyn.api.location.OsDetails;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.effector.ssh.SshEffectorTasks;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.entity.group.AbstractGroup;
import org.apache.brooklyn.entity.software.base.AbstractSoftwareProcessSshDriver;
import org.apache.brooklyn.entity.software.base.lifecycle.ScriptHelper;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.file.ArchiveUtils;
import org.apache.brooklyn.util.core.file.ArchiveUtils.ArchiveType;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.TaskBuilder;
import org.apache.brooklyn.util.core.task.system.ProcessTaskWrapper;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.repeat.Repeater;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;

import brooklyn.entity.container.DockerUtils;

public class DockerHostSshDriver extends AbstractSoftwareProcessSshDriver implements DockerHostDriver {

    public DockerHostSshDriver(DockerHostImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public Integer getDockerPort() {
        return getEntity().sensors().get(DockerHost.DOCKER_SSL_PORT);
    }

    /** {@inheritDoc} */
    @Override
    public String buildImage(String dockerfile, Optional<String> entrypoint, Optional<String> contextArchive, String name, boolean useSsh, Map<String, Object> substitutions) {
        String imageId;
        String imageDir =  Os.mergePaths(getRunDir(), name);
        if (!ArchiveType.UNKNOWN.equals(ArchiveType.of(dockerfile)) || Urls.isDirectory(dockerfile)) {
            ArchiveUtils.deploy(dockerfile, getMachine(), imageDir);
            imageId = buildDockerfileDirectory(name);
            log.info("Created base Dockerfile image with ID {}", imageId);
        } else {
            if (contextArchive.isPresent()) {
                ArchiveUtils.deploy(contextArchive.get(), getMachine(), imageDir);
            } else {
                ProcessTaskWrapper<Integer> task = SshEffectorTasks.ssh(format("mkdir -p %s", imageDir))
                        .machine(getMachine())
                        .newTask();
                DynamicTasks.queueIfPossible(task).executionContext(getEntity()).orSubmitAndBlock();
                int result = task.get();
                if (result != 0) throw new IllegalStateException("Error creating image directory: " + name);
            }
            copyTemplate(dockerfile, Os.mergePaths(name, DockerUtils.DOCKERFILE), false, substitutions);
            if (entrypoint.isPresent()) {
                copyResource(entrypoint.get(), Os.mergePaths(name, DockerUtils.ENTRYPOINT));
            }

            // Build the image using the files in the image directory
            imageId = buildDockerfileDirectory(name);
            log.info("Created Dockerfile image with ID {}", imageId);
        }

        if (useSsh) {
            // Update the image with the Clocker sshd Dockerfile
            copyTemplate(DockerUtils.SSHD_DOCKERFILE, Os.mergePaths(name, "Sshd" + DockerUtils.DOCKERFILE), false, substitutions);
            imageId = buildDockerfile("Sshd" + DockerUtils.DOCKERFILE, name);
            log.info("Created SSHable Dockerfile image with ID {}", imageId);
        }

        return imageId;
    }

    /** {@inheritDoc} */
    @Override
    public String layerSshableImageOn(String fullyQualifiedImageName) {
        checkNotNull(fullyQualifiedImageName, "fullyQualifiedImageName");
        copyTemplate(DockerUtils.SSHD_DOCKERFILE, Os.mergePaths(fullyQualifiedImageName, "Sshd" + DockerUtils.DOCKERFILE),
                true, ImmutableMap.<String, Object>of("fullyQualifiedImageName", fullyQualifiedImageName));
        String sshdImageId = buildDockerfile("Sshd" + DockerUtils.DOCKERFILE, fullyQualifiedImageName);
        log.info("Created SSH-based image from {} with ID {}", fullyQualifiedImageName, sshdImageId);

        return sshdImageId;
    }

    private String buildDockerfileDirectory(String name) {
        String build = format("build --rm -t %s %s",
                name, Os.mergePaths(getRunDir(), name));
        String stdout = ((DockerHost) getEntity()).runDockerCommandTimeout(build, Duration.minutes(20));
        String prefix = Strings.getFirstWordAfter(stdout, "Successfully built");

        return getImageId(prefix, name);
    }

    private String buildDockerfile(String dockerfile, String name) {
        String build = format("build --rm -t %s - < %s",
                name, Os.mergePaths(getRunDir(), name, dockerfile));
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
        return getEntity().config().get(DockerHost.EPEL_RELEASE);
    }

    public String getStorageOpts() {
        String driver = getEntity().config().get(DockerHost.DOCKER_STORAGE_DRIVER);
        if (Strings.isBlank(driver)) {
            return "";
        } else {
            return "-s " + Strings.toLowerCase(driver);
        }
    }

    @Override
    public String deployArchive(String url) {
        String volumeId = Identifiers.makeIdFromHash(url.hashCode());
        String path = Os.mergePaths(getRunDir(), volumeId);
        ArchiveUtils.deploy(url, getMachine(), path);
        return path;
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
        if (!osDetails.is64bit()) throw new IllegalStateException("Docker supports only 64bit OS");
        if (osDetails.isWindows()) throw new IllegalStateException("Windows operating system not yet supported by Docker");
        log.debug("Installing Docker on {} version {}", osDetails.getName(), osVersion);

        // Generate Linux kernel upgrade commands
        if (osDetails.isLinux()) {
            String kernelVersion = Strings.getFirstWord(((DockerHost) getEntity()).execCommand("uname -r"));
            String storage = Strings.toLowerCase(entity.config().get(DockerHost.DOCKER_STORAGE_DRIVER));
            if (!"devicemapper".equals(storage)) { // No kernel changes needed for devicemapper sadness as a servive
                int present = ((DockerHost) getEntity()).execCommandStatus("modprobe " + storage);
                if (present != 0) {
                    List<String> commands = MutableList.of();
                    if ("ubuntu".equalsIgnoreCase(osDetails.getName())) {
                        commands.add(installPackage("software-properties-common linux-generic-lts-vivid"));
                        executeKernelInstallation(commands);
                    }
                    if ("centos".equalsIgnoreCase(osDetails.getName())) {
                        // TODO differentiate between CentOS 6 and 7 and RHEL
                        commands.add(sudo("yum -y --nogpgcheck upgrade kernel"));
                        executeKernelInstallation(commands);
                    }
                }
            }
        }

        // Generate Docker install commands
        List<String> commands = Lists.newArrayList();
        if (osDetails.isMac()) {
            commands.add(alternatives(
                    ifExecutableElse1("boot2docker", "boot2docker status || boot2docker init"),
                    fail("Mac OSX install requires Boot2Docker preinstalled", 1)));
        }
        if (osDetails.isLinux()) {
            commands.add(INSTALL_CURL);
            if ("ubuntu".equalsIgnoreCase(osDetails.getName())) {
                commands.add(installDockerOnUbuntu());
            } else if ("centos".equalsIgnoreCase(osDetails.getName())) { // should work for RHEL also?
                commands.add(ifExecutableElse1("yum", useYum(osVersion, arch, getEpelRelease())));
                commands.add(installPackage(ImmutableMap.of("yum", "docker-io"), null));
                commands.add(sudo(format("curl https://get.docker.com/builds/Linux/x86_64/docker-%s -o /usr/bin/docker", getVersion())));
            } else {
                commands.add(installDockerFallback());
            }
        }
        newScript(INSTALLING)
                .body.append(commands)
                .failOnNonZeroResultCode()
                .execute();
    }

    private void executeKernelInstallation(List<String> commands) {
        newScript(INSTALLING + "kernel")
                .body.append(commands)
                .body.append(sudo("reboot"))
                .execute();

        // Wait until the Docker host is SSHable after the reboot
        // Don't check immediately; it could take a few seconds for rebooting to make the machine not ssh'able;
        // must not accidentally think it's rebooted before we've actually rebooted!
        Stopwatch stopwatchForReboot = Stopwatch.createStarted();
        Time.sleep(Duration.seconds(30));

        Task<Boolean> sshable = TaskBuilder.<Boolean> builder()
                .displayName("Waiting until host is SSHable")
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
    }

    private String useYum(String osVersion, String arch, String epelRelease) {
        String osMajorVersion = osVersion.substring(0, osVersion.lastIndexOf("."));
        return chainGroup(
                alternatives(
                        sudo("rpm -qa | grep epel-release"),
                        sudo(format("rpm -Uvh http://dl.fedoraproject.org/pub/epel/%s/%s/epel-release-%s.noarch.rpm", osMajorVersion, arch, epelRelease))));
    }

    @Override
    public String getVersion() {
        String version = super.getVersion();
        if (version.matches("^[0-9]+\\.[0-9]+$")) {
            version += ".0"; // Append minor version
        }
        return version;
    }

    private String installDockerOnUbuntu() {
        String version = getVersion();
        log.debug("Installing Docker version {} on Ubuntu", version);
        return chainGroup(
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
        return "curl -s https://get.docker.com/ | " + sudo("sh");
    }

    @Override
    public void customize() {
        if (isRunning()) {
            log.info("Stopping running Docker instance at {} before customising", getMachine());
            stop();
        }

        if (entity.config().get(DockerInfrastructure.DOCKER_GENERATE_TLS_CERTIFICATES)) {
            newScript(ImmutableMap.of(NON_STANDARD_LAYOUT, "true"), CUSTOMIZING)
                    .body.append(
                            String.format("cp ca-cert.pem %s/ca.pem", getRunDir()),
                            String.format("cp server-cert.pem %s/cert.pem", getRunDir()),
                            String.format("cp server-key.pem %s/key.pem", getRunDir()))
                .failOnNonZeroResultCode()
                .execute();
        }

        //Add the CA cert as an authorised docker CA for the first host.
        //This will be used for docker registrt etc.
        String hostnameOfFirstEntity = entity.sensors().get(AbstractGroup.FIRST).sensors().get(Attributes.HOSTNAME);
        String repoCAPath = "/etc/docker/certs.d/" + hostnameOfFirstEntity + ":5000";

        newScript(CUSTOMIZING)
                .body.append(
                chainGroup(sudo("mkdir -p " + repoCAPath),
                        sudo("cp ca.pem " + repoCAPath + "/ca.crt")))
                .failOnNonZeroResultCode()
                .execute();

        newScript(CUSTOMIZING + "-upstart")
                .body.append(
                        chain(
                                sudo("mkdir -p /etc/default"),
                                format("echo 'DOCKER_OPTS=\"-H tcp://0.0.0.0:%d -H unix:///var/run/docker.sock %s --tlsverify --tls --tlscert=%s/cert.pem --tlskey=%<s/key.pem --tlscacert=%<s/ca.pem\"' | ",
                                        getDockerPort(), getStorageOpts(), getRunDir()) + sudo("tee -a /etc/default/docker"),
                                sudo("groupadd -f docker"),
                                sudo(format("gpasswd -a %s docker", getMachine().getUser())),
                                sudo("newgrp docker")
                        ))
                .failOnNonZeroResultCode()
                .execute();

        //CentOS config
        newScript(CUSTOMIZING + "-centos")
                .body.append(
                        chain(
                                sudo("mkdir -p /etc/sysconfig"),
                                format("echo 'other_args=\"--selinux-enabled -H tcp://0.0.0.0:%d -H unix:///var/run/docker.sock -e lxc %s --tlsverify --tls --tlscert=%s/cert.pem --tlskey=%<s/key.pem --tlscacert=%<s/ca.pem\"' | ",
                                        getDockerPort(), getStorageOpts(), getRunDir()) + sudo("tee -a /etc/sysconfig/docker")
                        ))
                .failOnNonZeroResultCode()
                .execute();

        //SystemD
        String systemDConfigInstallPath = Os.mergePaths(getInstallDir(), "docker.service");

        copyTemplate("classpath://brooklyn/entity/container/docker/SystemDTemplate",
                systemDConfigInstallPath,
                true,
                ImmutableMap.of("args",
                        format("-H tcp://0.0.0.0:%d -H unix:///var/run/docker.sock %s --tlsverify --tls --tlscert=%s/cert.pem --tlskey=%<s/key.pem --tlscacert=%<s/ca.pem",
                                getDockerPort(), getStorageOpts(), getRunDir())));

        newScript(CUSTOMIZING + "-systemd")
                .body.append(
                        chain(
                                sudo("mkdir -p /etc/systemd/system"),
                                sudo(String.format("cp %s %s", systemDConfigInstallPath, "/etc/systemd/system/docker.service")),
                                ifExecutableElse0("systemctl", sudo("systemctl daemon-reload"))
                        ))
                .failOnNonZeroResultCode()
                .execute();

        // Configure volume mappings for the host
        Map<String, String> mapping = MutableMap.of();
        Map<String, String> volumes = getEntity().config().get(DockerHost.DOCKER_HOST_VOLUME_MAPPING);
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
        getEntity().sensors().set(DockerHost.DOCKER_HOST_VOLUME_MAPPING, mapping);
    }

    @Override
    public boolean isRunning() {
        ScriptHelper helper = newScript(CHECK_RUNNING)
                .body.append(
                        alternatives(
                                ifExecutableElse1("boot2docker", "boot2docker status"),
                                ifExecutableElse1("service", sudo("service docker status"))))
                .noExtraOutput() // otherwise Brooklyn appends 'check-running' and the method always returns true.
                .gatherOutput();
        helper.execute();
        return helper.getResultStdout().contains("running");
    }

    @Override
    public void stop() {
        newScript(STOPPING)
                .body.append(
                        alternatives(
                                ifExecutableElse1("boot2docker", "boot2docker down"),
                                ifExecutableElse1("service", sudo("service docker stop"))))
                .failOnNonZeroResultCode()
                .execute();
    }

    @Override
    public void launch() {
        newScript(LAUNCHING)
                .body.append(
                        alternatives(
                                ifExecutableElse1("boot2docker", "boot2docker up"),
                                ifExecutableElse1("service", sudo("service docker start"))))
                .failOnNonZeroResultCode()
                .uniqueSshConnection()
                .execute();
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
