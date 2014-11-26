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
package brooklyn.entity.basic;

import static brooklyn.entity.basic.ConfigKeys.*;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.config.ConfigKey;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.event.basic.TemplatedStringAttributeSensorAndConfigKey;
import brooklyn.util.internal.ssh.ShellTool;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.time.Duration;

import com.google.common.base.Preconditions;

/** Commonly used config keys, for use in entities. Similar to {@link Attributes}.
 * See also {@link BrooklynServerConfig} for config keys for controlling the server. */
public class BrooklynConfigKeys {

    @Deprecated /** @deprecated since 0.7.0 see BrooklynServerConfig#getPeristenceDir() and BrooklynServerConfigKeys#PERSISTENCE_DIR */
    public static final ConfigKey<String> BROOKLYN_PERSISTENCE_DIR = BrooklynServerConfig.PERSISTENCE_DIR;

    @Deprecated /** @deprecated since 0.7.0 use BrooklynServerConfig routines */
    public static final ConfigKey<String> BROOKLYN_DATA_DIR = BrooklynServerConfig.BROOKLYN_DATA_DIR;

    public static final ConfigKey<String> ONBOX_BASE_DIR = newStringConfigKey("onbox.base.dir",
            "Default base directory on target machines where Brooklyn config data is stored; " +
            "default depends on the location, either ~/brooklyn-managed-processes or /tmp/brooklyn-${username} on localhost");

    public static final ConfigKey<Boolean> SKIP_ON_BOX_BASE_DIR_RESOLUTION = ConfigKeys.newBooleanConfigKey("onbox.base.dir.skipResolution",
            "Whether to skip on-box directory resolution (which can require ssh'ing), and just assume the directory exists; can be set on machine or on entity", 
            false);

    // TODO Rename to VERSION, instead of SUGGESTED_VERSION? And declare as BasicAttributeSensorAndConfigKey?
    public static final ConfigKey<String> SUGGESTED_VERSION = newStringConfigKey("install.version", "Suggested version");

    public static final ConfigKey<String> INSTALL_UNIQUE_LABEL = ConfigKeys.newStringConfigKey("install.unique_label",
            "Provides a label which uniquely identifies an installation, used in the computation of the install dir; " +
            "this should include something readable, and must include a hash of all data which differentiates an installation " +
            "(e.g. version, plugins, etc), but should be the same where install dirs can be shared to allow for re-use");

    public static final ConfigKey<Boolean> ENTITY_STARTED = newBooleanConfigKey("entity.started", "Skip the startup process entirely, for running services");
    public static final ConfigKey<Boolean> ENTITY_RUNNING = newBooleanConfigKey("entity.running", "Skip the startup process entirely, if service already running");
    public static final ConfigKey<Boolean> SKIP_INSTALLATION = newBooleanConfigKey("install.skip", "Skip the driver install commands entirely, for pre-installed software");

    // The implementation in AbstractSoftwareSshDriver runs this command as an SSH command 
    public static final ConfigKey<String> PRE_INSTALL_COMMAND = ConfigKeys.newStringConfigKey("pre.install.command",
            "Command to be run prior to the install method being called on the driver");
    public static final ConfigKey<String> POST_INSTALL_COMMAND = ConfigKeys.newStringConfigKey("post.install.command",
            "Command to be run after the install method being called on the driver");
    public static final ConfigKey<String> PRE_LAUNCH_COMMAND = ConfigKeys.newStringConfigKey("pre.launch.command",
            "Command to be run prior to the launch method being called on the driver");
    public static final ConfigKey<String> POST_LAUNCH_COMMAND = ConfigKeys.newStringConfigKey("post.launch.command",
            "Command to be run after the launch method being called on the driver");

    public static final AttributeSensorAndConfigKey<String, String> INSTALL_DIR = new TemplatedStringAttributeSensorAndConfigKey("install.dir", "Directory for this software to be installed in",
            "${" +
            "config['"+ONBOX_BASE_DIR.getName()+"']!" +
            "config['"+BROOKLYN_DATA_DIR.getName()+"']!" +
            "'/<ERROR>-ONBOX_BASE_DIR-not-set'" +
            "}" +
            "/" +
            "installs/" +
            // the  var??  tests if it exists, passing value to ?string(if_present,if_absent)
            // the ! provides a default value afterwards, which is never used, but is required for parsing
            // when the config key is not available;
            // thus the below prefers the install.unique_label, but falls back to simple name
            // plus a version identifier *if* the version is explicitly set
            "${(config['install.unique_label']??)?string(config['install.unique_label']!'X'," +
            "(entity.entityType.simpleName)+" +
            "((config['install.version']??)?string('_'+(config['install.version']!'X'),''))" +
            ")}");

    public static final AttributeSensorAndConfigKey<String, String> RUN_DIR = new TemplatedStringAttributeSensorAndConfigKey("run.dir", "Directory for this software to be run from",
            "${" +
            "config['"+ONBOX_BASE_DIR.getName()+"']!" +
            "config['"+BROOKLYN_DATA_DIR.getName()+"']!" +
            "'/<ERROR>-ONBOX_BASE_DIR-not-set'" +
            "}" +
            "/" +
            "apps/${entity.applicationId}/" +
            "entities/${entity.entityType.simpleName}_" +
            "${entity.id}");

    public static final AttributeSensorAndConfigKey<String, String> EXPANDED_INSTALL_DIR = new TemplatedStringAttributeSensorAndConfigKey(
            "expandedinstall.dir", 
            "Directory for installed artifacts (e.g. expanded dir after unpacking .tgz)", 
            null);

    /** @deprecated since 0.7.0; use {@link #INSTALL_DIR} */
    public static final ConfigKey<String> SUGGESTED_INSTALL_DIR = INSTALL_DIR.getConfigKey();
    /** @deprecated since 0.7.0; use {@link #RUN_DIR} */
    public static final ConfigKey<String> SUGGESTED_RUN_DIR = RUN_DIR.getConfigKey();

    /*
     * Intention is to use these with DependentConfiguration.attributeWhenReady, to allow an entity's start
     * to block until dependents are ready. This is particularly useful when we want to block until a dependent
     * component is up, but this entity does not care about the dependent component's actual config values.
     */

    public static final ConfigKey<Boolean> START_LATCH = newBooleanConfigKey("start.latch", "Latch for blocking start until ready");
    public static final ConfigKey<Boolean> SETUP_LATCH = newBooleanConfigKey("setup.latch", "Latch for blocking setup until ready");
    public static final ConfigKey<Boolean> INSTALL_RESOURCES_LATCH = newBooleanConfigKey("resources.install.latch", "Latch for blocking install resources until ready");
    public static final ConfigKey<Boolean> INSTALL_LATCH = newBooleanConfigKey("install.latch", "Latch for blocking install until ready");
    public static final ConfigKey<Boolean> RUNTIME_RESOURCES_LATCH = newBooleanConfigKey("resources.runtime.latch", "Latch for blocking runtime resources until ready");
    public static final ConfigKey<Boolean> CUSTOMIZE_LATCH = newBooleanConfigKey("customize.latch", "Latch for blocking customize until ready");
    public static final ConfigKey<Boolean> LAUNCH_LATCH = newBooleanConfigKey("launch.latch", "Latch for blocking launch until ready");

    public static final ConfigKey<Duration> START_TIMEOUT = newConfigKey(
            "start.timeout", "Time to wait for process and for SERVICE_UP before failing (in seconds, default 2m)", Duration.seconds(120));

    /* selected properties from SshTool for external public access (e.g. putting on entities) */

    /** Public-facing global config keys for Brooklyn are defined in ConfigKeys, 
     * and have this prefix pre-prended to the config keys in this class. */
    public static final String BROOKLYN_SSH_CONFIG_KEY_PREFIX = "brooklyn.ssh.config.";

    // some checks (this line, and a few Preconditions below) that the remote values aren't null, 
    // because they have some funny circular references
    static { assert BROOKLYN_SSH_CONFIG_KEY_PREFIX.equals(SshTool.BROOKLYN_CONFIG_KEY_PREFIX) : "static final initializer classload ordering problem"; }

    public static final ConfigKey<String> SSH_TOOL_CLASS = newConfigKeyWithPrefix(BROOKLYN_SSH_CONFIG_KEY_PREFIX, 
            Preconditions.checkNotNull(SshTool.PROP_TOOL_CLASS, "static final initializer classload ordering problem"));

    public static final ConfigKey<String> SSH_CONFIG_HOST = newConfigKeyWithPrefix(BROOKLYN_SSH_CONFIG_KEY_PREFIX, SshTool.PROP_HOST);
    public static final ConfigKey<Integer> SSH_CONFIG_PORT = newConfigKeyWithPrefix(BROOKLYN_SSH_CONFIG_KEY_PREFIX, SshTool.PROP_PORT);
    public static final ConfigKey<String> SSH_CONFIG_USER = newConfigKeyWithPrefix(BROOKLYN_SSH_CONFIG_KEY_PREFIX, SshTool.PROP_USER);
    public static final ConfigKey<String> SSH_CONFIG_PASSWORD = newConfigKeyWithPrefix(BROOKLYN_SSH_CONFIG_KEY_PREFIX, SshTool.PROP_PASSWORD);

    public static final ConfigKey<String> SSH_CONFIG_SCRIPT_DIR = newConfigKeyWithPrefix(BROOKLYN_SSH_CONFIG_KEY_PREFIX, 
            Preconditions.checkNotNull(ShellTool.PROP_SCRIPT_DIR, "static final initializer classload ordering problem"));
    public static final ConfigKey<String> SSH_CONFIG_SCRIPT_HEADER = newConfigKeyWithPrefix(BROOKLYN_SSH_CONFIG_KEY_PREFIX, ShellTool.PROP_SCRIPT_HEADER);
    public static final ConfigKey<String> SSH_CONFIG_DIRECT_HEADER = newConfigKeyWithPrefix(BROOKLYN_SSH_CONFIG_KEY_PREFIX, ShellTool.PROP_DIRECT_HEADER);
    public static final ConfigKey<Boolean> SSH_CONFIG_NO_DELETE_SCRIPT = newConfigKeyWithPrefix(BROOKLYN_SSH_CONFIG_KEY_PREFIX, ShellTool.PROP_NO_DELETE_SCRIPT);

}
