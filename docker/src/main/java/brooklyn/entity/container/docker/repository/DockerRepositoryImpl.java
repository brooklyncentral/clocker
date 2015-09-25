package brooklyn.entity.container.docker.repository;


import org.apache.brooklyn.entity.software.base.SoftwareProcessImpl;

/**
 * Created by graememiller on 24/09/2015.
 */
public class DockerRepositoryImpl extends SoftwareProcessImpl implements DockerRepository {



    @Override
    public Class getDriverInterface() {
        return DockerRepositoryDriver.class;
    }
}
