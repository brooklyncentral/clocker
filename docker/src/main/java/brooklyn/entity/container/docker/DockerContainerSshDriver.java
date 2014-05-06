package brooklyn.entity.container.docker;

import java.util.concurrent.TimeUnit;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Entities;
import brooklyn.location.basic.SshMachineLocation;

public class DockerContainerSshDriver extends AbstractSoftwareProcessSshDriver implements DockerContainerDriver {

    public DockerContainerSshDriver(DockerContainerImpl entity, SshMachineLocation machine) {
        super(entity, machine);

        // Wait until the Docker Host has started up
        DockerHost dockerHost = getEntity().getConfig(DockerContainer.DOCKER_HOST);
        Entities.waitForServiceUp(dockerHost, dockerHost.getConfig(DockerHost.START_TIMEOUT));
    }

    public String getDockerContainerName() { return getEntity().getAttribute(DockerContainer.DOCKER_CONTAINER_NAME); }

    @Override
    public void install() {
        String containerName = getDockerContainerName();
        if (log.isDebugEnabled()) log.debug("Setup {}", containerName);

    }

    @Override
    public void customize() {
        String containerName = getDockerContainerName();
        if (log.isDebugEnabled()) log.debug("Creating {}", containerName);

    }

    @Override
    public void launch() {
        String containerName = getDockerContainerName();
        if (log.isDebugEnabled()) log.debug("Starting {}", containerName);

    }

    @Override
    public boolean isRunning() {
        String containerName = getDockerContainerName();
        if (log.isTraceEnabled()) log.trace("Checking {}", containerName);

        // TODO
        return true;
    }

    @Override
    public void stop() {
        String containerName = getDockerContainerName();
        if (log.isDebugEnabled()) log.debug("Stopping {}", containerName);

    }

    @Override
    public DockerContainerImpl getEntity() {
        return (DockerContainerImpl) super.getEntity();
    }

}
