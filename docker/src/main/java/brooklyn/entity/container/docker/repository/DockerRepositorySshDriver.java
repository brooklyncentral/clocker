package brooklyn.entity.container.docker.repository;

import brooklyn.entity.container.DockerAttributes;
import brooklyn.entity.container.docker.DockerContainer;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.entity.container.docker.DockerInfrastructureImpl;
import brooklyn.entity.container.docker.application.VanillaDockerApplication;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcessSshDriver;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.ssh.BashCommands;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.apache.brooklyn.util.ssh.BashCommands.chain;

/**
 * Created by graememiller on 24/09/2015.
 */
public class DockerRepositorySshDriver extends VanillaSoftwareProcessSshDriver implements DockerRepositoryDriver {


    public DockerRepositorySshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }


//    public DockerRepositorySshDriver(VanillaDockerApplicationImpl entity, SshMachineLocation machine) {
//        super(entity, machine);
//    }

    @Override
    public void install() {
        super.install();


        getMachine().installTo("classpath://brooklyn/entity/container/docker/repository/docker-repo-create-certs.sh", getInstallDir() + "/docker-repo-create-certs.sh");
        getMachine().installTo(entity.config().get(DockerInfrastructure.DOCKER_CA_CERTIFICATE_PATH), getInstallDir() + "/ca-cert.pem");
        getMachine().installTo(entity.config().get(DockerInfrastructure.DOCKER_CA_KEY_PATH), getInstallDir() + "/ca-key.pem");


        newScript(INSTALLING)
                .body.append(
                        chain(
                        "chmod 755 docker-repo-create-certs.sh",
                        BashCommands.sudo("./docker-repo-create-certs.sh "+entity.sensors().get(Attributes.HOSTNAME))))
                .failOnNonZeroResultCode()
                .uniqueSshConnection()
                .execute();



    }

    @Override
    public void launch() {

//        EntitySpec<VanillaDockerApplication> spec = EntitySpec.create(VanillaDockerApplication.class);
//        spec.configure(DockerAttributes.DOCKER_IMAGE_NAME, "registry");
//        spec.configure(DockerAttributes.DOCKER_DIRECT_PORTS, Collections.singletonList(5000));
//        spec.configure(DockerAttributes.DOCKER_HOST_VOLUME_MAPPING, Collections.singletonMap(this.getInstallDir() + "/certs", "/certs"));
//
//        Map<String, Object> dockerEnvironment = new HashMap<String, Object>();
//        dockerEnvironment.put("REGISTRY_HTTP_TLS_CERTIFICATE", "/certs/repo-cert.pem");
//        dockerEnvironment.put("REGISTRY_HTTP_TLS_KEY", "/certs/repo-key.pem");
//        spec.configure(DockerContainer.DOCKER_CONTAINER_ENVIRONMENT, dockerEnvironment);

        newScript(LAUNCHING)
                .body.append(
                chain(
                String.format(" docker run -d -p 5000:5000 --restart=always --name registry \\\n" +
                        "  -v %s/certs:/certs \\\n" +
                        "  -e REGISTRY_HTTP_TLS_CERTIFICATE=/certs/repo-cert.pem \\\n" +
                        "  -e REGISTRY_HTTP_TLS_KEY=/certs/repo-key.pem \\\n" +
                        "  registry:2", getInstallDir())))
                .failOnNonZeroResultCode()
                .uniqueSshConnection()
                .execute();

        //Entity.get
        //this.entity.config()

    }
}
