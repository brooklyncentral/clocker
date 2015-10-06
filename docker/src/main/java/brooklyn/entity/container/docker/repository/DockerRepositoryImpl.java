package brooklyn.entity.container.docker.repository;


import brooklyn.entity.container.DockerAttributes;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.entity.container.docker.application.VanillaDockerApplicationImpl;
import com.google.common.collect.ImmutableList;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.ssh.BashCommands;

import static org.apache.brooklyn.util.ssh.BashCommands.chain;

public class DockerRepositoryImpl extends VanillaDockerApplicationImpl implements DockerRepository {


    private String getSSHMachineInstallDir(){
        return config().get(DockerRepository.DOCKER_HOST).sensors().get(SoftwareProcess.INSTALL_DIR);
    }


    @Override
    public void init() {
        super.init();

        // TODO create sensor for registy port as well as config, and set sensor appropriately
        // TODO set Attributes.MAIN_URI to http://<brooklyn-accessible-host-and-port>/v2/
        // TODO see BrooklynAccessUtils usage in other classes
        // TODO implement connectSensors() and disconnectSensors()
        // TODO set SERVICE_UP based on HttpFeed returning 200 from MAIN_URI
        // TODO create sensor that gets /v2/_catalog and parses JSON for list of repositories/images

        DockerHost host = (DockerHost) config().get(DOCKER_HOST);

        config().set(DockerAttributes.DOCKER_PORT_BINDINGS, MutableMap.of(config().get(DOCKER_REGISTRY_PORT), 5000));

        String installDir = host.sensors().get(SoftwareProcess.INSTALL_DIR);
        config().set(DockerAttributes.DOCKER_HOST_VOLUME_MAPPING, MutableMap.of(Os.mergePaths(installDir, "certs"), "/certs"));

        // set location customizer to new inner class
        // customizer does the SshMachineLocation stuff below
        // is passed a DockerHostLocation, can get to SshMachineLocation from there


        SshMachineLocation sshMachine = host.getDynamicLocation().getMachine();
        sshMachine.installTo("classpath://brooklyn/entity/container/docker/repository/docker-repo-create-certs.sh", Os.mergePaths(getSSHMachineInstallDir(), "docker-repo-create-certs.sh"));
        sshMachine.installTo(config().get(DockerInfrastructure.DOCKER_CA_CERTIFICATE_PATH), Os.mergePaths(getSSHMachineInstallDir(), "ca-cert.pem"));
        sshMachine.installTo(config().get(DockerInfrastructure.DOCKER_CA_KEY_PATH), Os.mergePaths(getSSHMachineInstallDir(), "ca-key.pem"));

        int result = sshMachine.execCommands("installCerts",
                ImmutableList.of(
                        chain(
                                "cd "+getSSHMachineInstallDir(),
                                BashCommands.sudo("chmod 755 " + Os.mergePaths(getSSHMachineInstallDir(), "docker-repo-create-certs.sh")),
                                BashCommands.sudo(String.format("%s %s %s",
                                                Os.mergePaths(getSSHMachineInstallDir(), "docker-repo-create-certs.sh"),
                                                host.sensors().get(Attributes.HOSTNAME),
                                                getSSHMachineInstallDir())
                        ))));

        if (result != 0) {
            throw new IllegalStateException("Could not create certificates for docker registry");
        }
    }
}
