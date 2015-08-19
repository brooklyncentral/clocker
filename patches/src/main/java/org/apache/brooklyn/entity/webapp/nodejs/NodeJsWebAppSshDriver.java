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
package org.apache.brooklyn.entity.webapp.nodejs;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.entity.software.base.AbstractSoftwareProcessSshDriver;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.software.base.lifecycle.ScriptHelper;
import org.apache.brooklyn.entity.webapp.WebAppService;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.file.ArchiveUtils;
import org.apache.brooklyn.util.net.Networking;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.ssh.BashCommands;
import org.apache.brooklyn.util.text.Strings;

public class NodeJsWebAppSshDriver extends AbstractSoftwareProcessSshDriver implements NodeJsWebAppDriver {

    private static final Logger LOG = LoggerFactory.getLogger(NodeJsWebAppService.class);

    public NodeJsWebAppSshDriver(NodeJsWebAppServiceImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    public NodeJsWebAppServiceImpl getEntity() {
        return (NodeJsWebAppServiceImpl) super.getEntity();
    }

    @Override
    public Integer getHttpPort() {
        return getEntity().getAttribute(Attributes.HTTP_PORT);
    }

    @Override
    public String getAppDir() {
        return Os.mergePaths(getRunDir(), getEntity().getConfig(NodeJsWebAppService.APP_NAME));
    }

    @Override
    public void postLaunch() {
        String rootUrl = String.format("http://%s:%d/", getHostname(), getHttpPort());
        entity.setAttribute(Attributes.MAIN_URI, URI.create(rootUrl));
        entity.setAttribute(WebAppService.ROOT_URL, rootUrl);
    }

    protected Map<String, Integer> getPortMap() {
        return MutableMap.of("http", getHttpPort());
    }

    @Override
    public Set<Integer> getPortsUsed() {
        return ImmutableSet.<Integer>builder()
                .addAll(super.getPortsUsed())
                .addAll(getPortMap().values())
                .build();
    }

    // TODO Suggest that other entities follow this pattern as well: check for port availability early
    // to report failures early, and in case getShellEnvironment() tries to convert any null port numbers
    // to int.
    @Override
    public void preInstall() {
        super.preInstall();
        Map<String,String> shellEnvironment = getShellEnvironment();
        ScriptHelper setEnv = newScript(CUSTOMIZING);
        if (shellEnvironment != null) {
            for(Entry<String, String>  env : shellEnvironment.entrySet()) {
                setEnv.body.append("export " + env.getKey() + "=" + env.getValue());
            }
        }
        setEnv.execute();
        Networking.checkPortsValid(getPortMap());
    }
    
    @Override
    public void install() {
        LOG.info("Installing Node.JS {}", getEntity().getConfig(SoftwareProcess.SUGGESTED_VERSION));

        List<String> commands = MutableList.<String>builder()
                .add(BashCommands.INSTALL_CURL)
                .add(BashCommands.ifExecutableElse0("apt-get", BashCommands.chain(
                        BashCommands.installPackage("software-properties-common python-software-properties python g++ make"),
                        BashCommands.sudo("add-apt-repository ppa:chris-lea/node.js"))))
                .add(BashCommands.installPackage(MutableMap.of("yum", "git nodejs npm", "apt", "git-core nodejs"), null))
                .add("mkdir \"$HOME/.npm\"")
                .add(BashCommands.sudo("npm install -g n"))
                .add(BashCommands.sudo("n " + getEntity().getConfig(SoftwareProcess.SUGGESTED_VERSION)))
                .build();

        newScript(INSTALLING)
                .body.append(commands)
                .execute();
    }

    @Override
    public void customize() {
        List<String> commands = Lists.newLinkedList();

        String gitRepoUrl = getEntity().getConfig(NodeJsWebAppService.APP_GIT_REPOSITORY_URL);
        String archiveUrl = getEntity().getConfig(NodeJsWebAppService.APP_ARCHIVE_URL);
        String appName = getEntity().getConfig(NodeJsWebAppService.APP_NAME);
        if (Strings.isNonBlank(gitRepoUrl) && Strings.isNonBlank(archiveUrl)) {
            throw new IllegalStateException("Only one of Git or archive URL must be set for " + getEntity());
        } else if (Strings.isNonBlank(gitRepoUrl)) {
            commands.add(String.format("git clone %s %s", gitRepoUrl, appName));
            commands.add(String.format("cd %s", appName));
        } else if (Strings.isNonBlank(archiveUrl)) {
            ArchiveUtils.deploy(archiveUrl, getMachine(), getRunDir());
        } else {
            throw new IllegalStateException("At least one of Git or archive URL must be set for " + getEntity());
        }

        commands.add(BashCommands.ifFileExistsElse1("package.json", "npm install"));
        List<String> packages = getEntity().getConfig(NodeJsWebAppService.NODE_PACKAGE_LIST);
        if (packages != null && packages.size() > 0) {
            commands.add(BashCommands.sudo("npm install -g " + Joiner.on(' ').join(packages)));
        }

        newScript(CUSTOMIZING)
                .body.append(commands)
                .execute();
    }

    @Override
    public void launch() {
        List<String> commands = Lists.newLinkedList();

        String appName = getEntity().getConfig(NodeJsWebAppService.APP_NAME);
        String appFile = getEntity().getConfig(NodeJsWebAppService.APP_FILE);
        String appCommand = getEntity().getConfig(NodeJsWebAppService.APP_COMMAND);
        String appCommandLine = getEntity().getConfig(NodeJsWebAppService.APP_COMMAND_LINE);

        if (Strings.isBlank(appCommandLine)) {
            appCommandLine = appCommand + " " + appFile;
        }

        // Ensure global NPM modules are on Node's path.
        commands.add("export NODE_PATH=\"$NODE_PATH:$(npm root -g)\"");
        commands.add(String.format("cd %s", Os.mergePathsUnix(getRunDir(), appName)));
        commands.add("nohup " + appCommandLine + " > console.out 2>&1 &");

        newScript(MutableMap.of(USE_PID_FILE, true), LAUNCHING)
                .body.append(commands)
                .execute();
    }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of(USE_PID_FILE, true), CHECK_RUNNING).execute() == 0;
    }

    @Override
    public void stop() {
        newScript(MutableMap.of(USE_PID_FILE, true), STOPPING).execute();
    }

    @Override
    public Map<String, String> getShellEnvironment() {
        return MutableMap.<String, String>builder().putAll(super.getShellEnvironment())
                .put("PORT", Integer.toString(getHttpPort()))
                .build();
    }

}
