/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package clocker.docker.entity.util;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import org.jclouds.compute.ComputeService;

import org.apache.brooklyn.core.effector.ssh.SshEffectorTasks;
import org.apache.brooklyn.location.jclouds.BasicJcloudsLocationCustomizer;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsMachineLocation;
import org.apache.brooklyn.location.jclouds.JcloudsSshMachineLocation;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.ssh.BashCommands;

/**
 * Fix hostname issues.
 */
public class JcloudsHostnameCustomizer extends BasicJcloudsLocationCustomizer {

    public static final Logger LOG = LoggerFactory.getLogger(JcloudsHostnameCustomizer.class);

    public static JcloudsHostnameCustomizer instanceOf() {
        return new JcloudsHostnameCustomizer();
    }

    @Override
    public void customize(JcloudsLocation location, ComputeService computeService, JcloudsMachineLocation machine) {
        JcloudsSshMachineLocation ssh = (JcloudsSshMachineLocation) machine;
        String name = ssh.getOptionalNode().get().getName();

        List<String> commands = ImmutableList.of(
                String.format("echo %s | ( %s )", name, BashCommands.sudo("tee /etc/hostname")),
                String.format("echo 127.0.0.1 localhost | ( %s )", BashCommands.sudo("tee /etc/hosts")));
        DynamicTasks.queue(SshEffectorTasks.ssh(commands)
                .machine(ssh)
                .requiringExitCodeZero()).block();
        LOG.debug("Set {} hostname to {}", new Object[] { ssh, name });
    }
}
