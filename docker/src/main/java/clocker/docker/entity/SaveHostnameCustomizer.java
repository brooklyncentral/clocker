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
package clocker.docker.entity;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.Template;
import org.jclouds.softlayer.compute.options.SoftLayerTemplateOptions;

import org.apache.brooklyn.core.effector.ssh.SshEffectorTasks;
import org.apache.brooklyn.location.jclouds.BasicJcloudsLocationCustomizer;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsMachineLocation;
import org.apache.brooklyn.location.jclouds.JcloudsSshMachineLocation;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.ssh.BashCommands;

/**
 * Saves the hostname as the jclouds machine name.
 */
public class SaveHostnameCustomizer extends BasicJcloudsLocationCustomizer {

    public static final Logger LOG = LoggerFactory.getLogger(SaveHostnameCustomizer.class);

    public static SaveHostnameCustomizer instanceOf() {
        return new SaveHostnameCustomizer();
    }

    @Override
    public void customize(JcloudsLocation location, ComputeService computeService, Template template) {
        SoftLayerTemplateOptions options = (SoftLayerTemplateOptions) template.getOptions();
        String name = options.getUserMetadata().get("Name");
        options.nodeNames(ImmutableSet.of(name));
    }

    @Override
    public void customize(JcloudsLocation location, ComputeService computeService, JcloudsMachineLocation machine) {
        JcloudsSshMachineLocation ssh = (JcloudsSshMachineLocation) machine;
        String name = ssh.getHostname();

        List<String> commands = ImmutableList.of(
                BashCommands.sudo(String.format("hostname %s", name)),
                String.format("echo %s | ( %s )", name, BashCommands.sudo("tee /etc/hostname")),
                BashCommands.sudo("sed -i \"/(none)/d\" /etc/hosts"));
        DynamicTasks.queue(SshEffectorTasks.ssh(commands)
                .machine(ssh)
                .requiringExitCodeZero()).block();
        LOG.debug("Set {} hostname to {}", new Object[] { ssh, name });
    }
}
