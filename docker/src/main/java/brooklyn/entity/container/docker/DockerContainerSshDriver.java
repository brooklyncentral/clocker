package brooklyn.entity.container.docker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Entities;
import brooklyn.location.basic.SshMachineLocation;

public class DockerContainerSshDriver extends AbstractSoftwareProcessSshDriver implements DockerContainerDriver {

    private static final Logger LOG = LoggerFactory.getLogger(DockerContainerSshDriver.class);

    public DockerContainerSshDriver(DockerContainerImpl entity, SshMachineLocation machine) {
        super(entity, machine);

        // Wait until the Docker Host has started up
        DockerHost dockerHost = getEntity().getConfig(DockerContainer.DOCKER_HOST);
        Entities.waitForServiceUp(dockerHost);
    }

    public String getDockerContainerName() { return getEntity().getAttribute(DockerContainer.DOCKER_CONTAINER_NAME); }

    @Override
    public void install() {
        String containerName = getDockerContainerName();
        if (LOG.isDebugEnabled()) LOG.debug("Setup {}", containerName);

    }

    @Override
    public void customize() {
        String containerName = getDockerContainerName();
        if (LOG.isDebugEnabled()) LOG.debug("Creating {}", containerName);

    }

    @Override
    public void launch() {
        String containerName = getDockerContainerName();
        if (LOG.isDebugEnabled()) LOG.debug("Starting {}", containerName);

    }

    @Override
    public boolean isRunning() {
        String containerName = getDockerContainerName();
        if (LOG.isTraceEnabled()) LOG.trace("Checking {}", containerName);

        // TODO
        return true;
    }

    @Override
    public void stop() {
        String containerName = getDockerContainerName();
        if (LOG.isDebugEnabled()) LOG.debug("Stopping {}", containerName);

    }

    @Override
    public DockerContainerImpl getEntity() {
        return (DockerContainerImpl) super.getEntity();
    }

}
