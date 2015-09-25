package brooklyn.entity.container.docker.repository;

import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;

/**
 * Created by graememiller on 24/09/2015.
 */
@ImplementedBy(DockerRepositoryImpl.class)
public interface DockerRepository extends SoftwareProcess {
}
