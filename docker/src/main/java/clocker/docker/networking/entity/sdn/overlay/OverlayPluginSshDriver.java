/*
 * Copyright 2014-2016 by Cloudsoft Corporation Limited
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
package clocker.docker.networking.entity.sdn.overlay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clocker.docker.networking.entity.sdn.DockerNetworkAgentSshDriver;

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.location.ssh.SshMachineLocation;

public class OverlayPluginSshDriver extends DockerNetworkAgentSshDriver implements OverlayPluginDriver {

    private static final Logger LOG = LoggerFactory.getLogger(OverlayPlugin.class);

    public OverlayPluginSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public String getDockerNetworkDriver() {
        return "overlay";
    }

    @Override
    public void install() {
        LOG.info("Docker libnetwork overlay plugin installed");
    }

    @Override
    public void customize() { }

    @Override
    public void launch() { }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public void stop() { }

}
