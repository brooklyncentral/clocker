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
package brooklyn.entity.proxy.haproxy;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.core.location.Machines;
import org.apache.brooklyn.entity.proxy.LoadBalancer;
import org.apache.brooklyn.entity.software.base.AbstractSoftwareProcessSshDriver;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.os.Os;

public class HAProxySshDriver extends AbstractSoftwareProcessSshDriver implements HAProxyDriver {

    private static final Logger LOG = LoggerFactory.getLogger(HAProxySshDriver.class);

    public HAProxySshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public void install() {
        throw new UnsupportedOperationException("Driver expected to be used in a container with HAProxy installed");
    }

    @Override
    public void customize() {
        reconfigureService();
    }

    @Override
    public void launch() {
        StringBuilder command = new StringBuilder(Os.mergePathsUnix(getInstallDir(), "haproxy"))
                .append(" -D") // daemon
                .append(" -p ").append(getPidFileLocation())
                .append(" -f ").append(getConfigFileLocation())
                // must be the last argument
                .append(" -sf ").append(" $(cat ").append(getPidFileLocation()).append(")");
        newScript(LAUNCHING)
                .body.append(command)
                .failOnNonZeroResultCode()
                .execute();
    }

    @Override
    public void stop() {
        newScript(MutableMap.of(USE_PID_FILE, true), STOPPING).execute();
    }

    @Override
    public boolean isRunning() {
        return 0 == newScript(MutableMap.of(USE_PID_FILE, getPidFileLocation()), CHECK_RUNNING).execute();
    }

    public String getPidFileLocation() {
        return Os.mergePathsUnix(getRunDir(), "pid");
    }

    @Override
    public void reconfigureService() {
        Map<Entity, String> targets = getEntity().sensors().get(HAProxyController.SERVER_POOL_TARGETS);
        LOG.info("Reconfiguring {} with: {}", getEntity(), targets.values());
        for (Entity server : targets.keySet()) {
            Maybe<SshMachineLocation> machine = Machines.findUniqueMachineLocation(server.getLocations(), SshMachineLocation.class);
            if (machine.isPresentAndNonNull()) {
            }
        }
        Map<String, Object> substitutions = ImmutableMap.<String, Object>builder()
                .put("port", getEntity().config().get(LoadBalancer.PROXY_HTTP_PORT))
                .build();
        String template = getEntity().config().get(HAProxyController.HAPROXY_CONFIG_TEMPLATE_URL);
        copyTemplate(template, getConfigFileLocation(), true, substitutions);
        launch();
        LOG.debug("HAProxy re-configured on: {}", getEntity());
    }

    private String getConfigFileLocation() {
        return Os.mergePathsUnix(getRunDir(), "haproxy.cfg");
    }

    // For use in templates
    public String getFrontendMode() {
        return getEntity().config().get(HAProxyController.FRONTEND_MODE);
    }

    public String getBackendMode() {
        return getEntity().config().get(HAProxyController.BACKEND_MODE);
    }

    public String getBindAddress() {
        String host = Optional.fromNullable(getEntity().config().get(HAProxyController.BIND_ADDRESS)).or("*");
        Integer port = getEntity().sensors().get(HAProxyController.PROXY_HTTP_PORT);
        return host + ":" + port;
    }

}
