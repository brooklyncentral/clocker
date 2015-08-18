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
package brooklyn.location.docker;

import java.util.List;

import com.google.common.reflect.TypeToken;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.location.basic.SshMachineLocation;

import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.container.docker.DockerContainer;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.container.docker.DockerInfrastructure;

public interface DockerVirtualLocation extends Location {

    ConfigKey<MachineProvisioningLocation<SshMachineLocation>> PROVISIONER =
            ConfigKeys.newConfigKey(new TypeToken<MachineProvisioningLocation<SshMachineLocation>>() { },
                    "docker.provisioner", "The underlying provisioner for VMs");

    ConfigKey<DockerInfrastructure> INFRASTRUCTURE =
            ConfigKeys.newConfigKey(DockerInfrastructure.class, "docker.infrastructure", "The Docker infrastructure entity");

    ConfigKey<SshMachineLocation> MACHINE =
            ConfigKeys.newConfigKey(SshMachineLocation.class, "docker.machine", "The underlying SSHable VM");

    ConfigKey<DockerHost> HOST =
            ConfigKeys.newConfigKey(DockerHost.class, "docker.host", "The underlying Docker host entity");

    ConfigKey<DockerContainer> CONTAINER =
            ConfigKeys.newConfigKey(DockerContainer.class, "docker.container", "The underlying Docker container entity");

    String PREFIX = "docker-";

    List<Entity> getDockerContainerList();

    List<Entity> getDockerHostList();

    DockerInfrastructure getDockerInfrastructure();

}
