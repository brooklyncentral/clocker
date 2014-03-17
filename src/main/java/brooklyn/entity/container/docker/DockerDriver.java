package brooklyn.entity.container.docker;

import brooklyn.entity.basic.SoftwareProcessDriver;

/**
 *
 * The {@link brooklyn.entity.basic.SoftwareProcessDriver} for Docker.
 *
 * @author Andrea Turli
 */
public interface DockerDriver extends SoftwareProcessDriver {

   Integer getDockerPort();

}
