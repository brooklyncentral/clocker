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
package org.apache.brooklyn.core.sensor.ssh;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;

import org.apache.brooklyn.api.entity.EntityInitializer;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.effector.AddSensor;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.sensor.http.HttpRequestSensor;
import org.apache.brooklyn.feed.ssh.SshFeed;
import org.apache.brooklyn.feed.ssh.SshPollConfig;
import org.apache.brooklyn.feed.ssh.SshValueFunctions;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.text.Strings;

/** 
 * Configurable {@link EntityInitializer} which adds an SSH sensor feed running the <code>command</code> supplied
 * in order to populate the sensor with the indicated <code>name</code>. Note that the <code>targetType</code> is ignored,
 * and always set to {@link String}.
 *
 * @see HttpRequestSensor
 */
@Beta
public final class SshCommandSensor<T> extends AddSensor<T> {

    private static final Logger LOG = LoggerFactory.getLogger(SshCommandSensor.class);

    public static final ConfigKey<Boolean> TRIM_OUTPUT = ConfigKeys.newBooleanConfigKey("trim", "Trim SSH command output", Boolean.FALSE);
    public static final ConfigKey<String> SENSOR_COMMAND = ConfigKeys.newStringConfigKey("command", "SSH command to execute for sensor");
    public static final ConfigKey<String> SENSOR_EXECUTION_DIR = ConfigKeys.newStringConfigKey("executionDir", "Directory where the command should run; "
        + "if not supplied, executes in the entity's run dir (or home dir if no run dir is defined); "
        + "use '~' to always execute in the home dir, or 'custom-feed/' to execute in a custom-feed dir relative to the run dir");

    protected final String command;
    protected final boolean trim;
    protected final String executionDir;

    public SshCommandSensor(final ConfigBag params) {
        super(params);

        // TODO create a supplier for the command string to support attribute embedding
        command = Preconditions.checkNotNull(params.get(SENSOR_COMMAND), "command");
        trim = params.get(TRIM_OUTPUT);
        executionDir = params.get(SENSOR_EXECUTION_DIR);
    }

    @Override
    public void apply(final EntityLocal entity) {
        super.apply(entity);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Adding SSH sensor {} to {}", name, entity);
        }

        Supplier<Map<String,String>> envSupplier = new Supplier<Map<String,String>>() {
            @Override
            public Map<String, String> get() {
                return MutableMap.copyOf(Strings.toStringMap(entity.getConfig(BrooklynConfigKeys.SHELL_ENVIRONMENT), ""));
            }
        };

        Supplier<String> commandSupplier = new Supplier<String>() {
            @Override
            public String get() {
                return makeCommandExecutingInDirectory(command, executionDir, entity);
            }
        };

        SshPollConfig<T> pollConfig = new SshPollConfig<T>(sensor)
                .period(period)
                .env(envSupplier)
                .command(commandSupplier)
                .checkSuccess(SshValueFunctions.exitStatusEquals(0))
                .onFailureOrException(Functions.constant((T) null))
                .onSuccess(Functions.compose(new Function<String, T>() {
                        @Override
                        public T apply(String input) {
                            return TypeCoercions.coerce(trim ? input.trim() : input, getType(type));
                        }}, SshValueFunctions.stdout()));

        SshFeed.builder()
                .entity(entity)
                .onlyIfServiceUp()
                .poll(pollConfig)
                .build();
    }

    @Beta
    public static String makeCommandExecutingInDirectory(String command, String executionDir, EntityLocal entity) {
        String finalCommand = command;
        String execDir = executionDir;
        if (Strings.isBlank(execDir)) {
            // default to run dir
            execDir = entity.getAttribute(BrooklynConfigKeys.RUN_DIR);
            // if no run dir, default to home
            if (Strings.isBlank(execDir)) {
                execDir = "~";
            }
        } else if (!Os.isAbsolutish(execDir)) {
            // relative paths taken wrt run dir
            String runDir = entity.getAttribute(BrooklynConfigKeys.RUN_DIR);
            if (!Strings.isBlank(runDir)) {
                execDir = Os.mergePaths(runDir, execDir);
            }
        }
        if (!"~".equals(execDir)) {
            finalCommand = "mkdir -p '"+execDir+"' && cd '"+execDir+"' && "+finalCommand;
        }
        return finalCommand;
    }

}
