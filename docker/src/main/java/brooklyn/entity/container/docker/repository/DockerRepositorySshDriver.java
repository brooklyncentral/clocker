package brooklyn.entity.container.docker.repository;

import brooklyn.entity.container.docker.application.VanillaDockerApplicationImpl;
import brooklyn.entity.container.docker.application.VanillaDockerApplicationSshDriver;
import org.apache.brooklyn.location.ssh.SshMachineLocation;

public class DockerRepositorySshDriver extends VanillaDockerApplicationSshDriver implements DockerRepositoryDriver {

    public DockerRepositorySshDriver(VanillaDockerApplicationImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

}
