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

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.event.basic.MapConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.reflect.TypeToken;

public interface SoftwareProcess extends Entity, Startable {

    AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;
    AttributeSensor<String> ADDRESS = Attributes.ADDRESS;
    AttributeSensor<String> SUBNET_HOSTNAME = Attributes.SUBNET_HOSTNAME;
    AttributeSensor<String> SUBNET_ADDRESS = Attributes.SUBNET_ADDRESS;

    @SetFromFlag("startTimeout")
    ConfigKey<Duration> START_TIMEOUT = BrooklynConfigKeys.START_TIMEOUT;

    @SetFromFlag("startLatch")
    ConfigKey<Boolean> START_LATCH = BrooklynConfigKeys.START_LATCH;

    @SetFromFlag("setupLatch")
    ConfigKey<Boolean> SETUP_LATCH = BrooklynConfigKeys.SETUP_LATCH;

    @SetFromFlag("installResourcesLatch")
    ConfigKey<Boolean> INSTALL_RESOURCES_LATCH = BrooklynConfigKeys.INSTALL_RESOURCES_LATCH;

    @SetFromFlag("installLatch")
    ConfigKey<Boolean> INSTALL_LATCH = BrooklynConfigKeys.INSTALL_LATCH;

    @SetFromFlag("runtimeResourcesLatch")
    ConfigKey<Boolean> RUNTIME_RESOURCES_LATCH = BrooklynConfigKeys.RUNTIME_RESOURCES_LATCH;

    @SetFromFlag("customizeLatch")
    ConfigKey<Boolean> CUSTOMIZE_LATCH = BrooklynConfigKeys.CUSTOMIZE_LATCH;

    @SetFromFlag("launchLatch")
    ConfigKey<Boolean> LAUNCH_LATCH = BrooklynConfigKeys.LAUNCH_LATCH;

    @SetFromFlag("entityStarted")
    ConfigKey<Boolean> ENTITY_STARTED = BrooklynConfigKeys.ENTITY_STARTED;

    @SetFromFlag("entityRunning")
    ConfigKey<Boolean> ENTITY_RUNNING = BrooklynConfigKeys.ENTITY_RUNNING;

    @SetFromFlag("skipInstall")
    ConfigKey<Boolean> SKIP_INSTALLATION = BrooklynConfigKeys.SKIP_INSTALLATION;

    @SetFromFlag("preInstallCommand")
    ConfigKey<String> PRE_INSTALL_COMMAND = BrooklynConfigKeys.PRE_INSTALL_COMMAND;

    @SetFromFlag("postInstallCommand")
    ConfigKey<String> POST_INSTALL_COMMAND = BrooklynConfigKeys.POST_INSTALL_COMMAND;

    @SetFromFlag("preLaunchCommand")
    ConfigKey<String> PRE_LAUNCH_COMMAND = BrooklynConfigKeys.PRE_LAUNCH_COMMAND;

    @SetFromFlag("postLaunchCommand")
    ConfigKey<String> POST_LAUNCH_COMMAND = BrooklynConfigKeys.POST_LAUNCH_COMMAND;

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = BrooklynConfigKeys.SUGGESTED_VERSION;

    @SetFromFlag("downloadUrl")
    AttributeSensorAndConfigKey<String,String> DOWNLOAD_URL = Attributes.DOWNLOAD_URL;

    @SetFromFlag("downloadAddonUrls")
    AttributeSensorAndConfigKey<Map<String,String>,Map<String,String>> DOWNLOAD_ADDON_URLS = Attributes.DOWNLOAD_ADDON_URLS;

    @SetFromFlag("installLabel")
    ConfigKey<String> INSTALL_UNIQUE_LABEL = BrooklynConfigKeys.INSTALL_UNIQUE_LABEL;

    @SetFromFlag("expandedInstallDir")
    AttributeSensorAndConfigKey<String,String> EXPANDED_INSTALL_DIR = BrooklynConfigKeys.EXPANDED_INSTALL_DIR;

    @SetFromFlag("installDir")
    AttributeSensorAndConfigKey<String,String> INSTALL_DIR = BrooklynConfigKeys.INSTALL_DIR;
    @Deprecated
    ConfigKey<String> SUGGESTED_INSTALL_DIR = BrooklynConfigKeys.SUGGESTED_INSTALL_DIR;

    @SetFromFlag("runDir")
    AttributeSensorAndConfigKey<String,String> RUN_DIR = BrooklynConfigKeys.RUN_DIR;
    @Deprecated
    ConfigKey<String> SUGGESTED_RUN_DIR = BrooklynConfigKeys.SUGGESTED_RUN_DIR;

    /**
     * Files to be copied to the server before install.
     * <p>
     * Map of {@code classpath://foo/file.txt} (or other url) source to destination path,
     * as {@code subdir/file} relative to installation directory or {@code /absolute/path/to/file}.
     *
     * @see #INSTALL_TEMPLATES
     */
    @Beta
    @SuppressWarnings("serial")
    @SetFromFlag("installFiles")
    ConfigKey<Map<String, String>> INSTALL_FILES = ConfigKeys.newConfigKey(new TypeToken<Map<String, String>>() { },
            "files.install", "Mapping of files, to be copied before install, to destination name relative to installDir");

    /**
     * Templates to be filled in and then copied to the server before install.
     *
     * @see #INSTALL_FILES
     */
    @Beta
    @SuppressWarnings("serial")
    @SetFromFlag("installTemplates")
    ConfigKey<Map<String, String>> INSTALL_TEMPLATES = ConfigKeys.newConfigKey(new TypeToken<Map<String, String>>() { },
            "templates.install", "Mapping of templates, to be filled in and copied before install, to destination name relative to installDir");

    /**
     * Files to be copied to the server after customisation.
     * <p>
     * Map of {@code classpath://foo/file.txt} (or other url) source to destination path,
     * as {@code subdir/file} relative to runtime directory or {@code /absolute/path/to/file}.
     *
     * @see #RUNTIME_TEMPLATES
     */
    @Beta
    @SuppressWarnings("serial")
    @SetFromFlag("runtimeFiles")
    ConfigKey<Map<String, String>> RUNTIME_FILES = ConfigKeys.newConfigKey(new TypeToken<Map<String, String>>() { },
            "files.runtime", "Mapping of files, to be copied before customisation, to destination name relative to runDir");

    /**
     * Templates to be filled in and then copied to the server after customisation.
     *
     * @see #RUNTIME_FILES
     */
    @Beta
    @SuppressWarnings("serial")
    @SetFromFlag("runtimeTemplates")
    ConfigKey<Map<String, String>> RUNTIME_TEMPLATES = ConfigKeys.newConfigKey(new TypeToken<Map<String, String>>() { },
            "templates.runtime", "Mapping of templates, to be filled in and copied before customisation, to destination name relative to runDir");

    @SetFromFlag("env")
    MapConfigKey<Object> SHELL_ENVIRONMENT = new MapConfigKey<Object>(Object.class,
            "shell.env", "Map of environment variables to pass to the runtime shell", MutableMap.<String,Object>of());

    @SetFromFlag("provisioningProperties")
    MapConfigKey<Object> PROVISIONING_PROPERTIES = new MapConfigKey<Object>(Object.class,
            "provisioning.properties", "Custom properties to be passed in when provisioning a new machine", MutableMap.<String,Object>of());

    @SetFromFlag("maxRebindSensorsDelay")
    ConfigKey<Duration> MAXIMUM_REBIND_SENSOR_CONNECT_DELAY = ConfigKeys.newConfigKey(Duration.class,
            "softwareProcess.maxSensorRebindDelay",
            "The maximum delay to apply when reconnecting sensors when rebinding to this entity. " +
                    "Brooklyn will wait a random amount of time, up to the value of this config key, to " +
                    "avoid a thundering herd problem when the entity shares its machine with " +
                    "several others. Set to null or to 0 to disable any delay.",
            Duration.TEN_SECONDS);

    /** controls the behavior when starting (stop, restart) {@link Startable} children as part of the start (stop, restart) effector on this entity
     * <p>
     * (NB: restarts are currently not propagated to children in the default {@link SoftwareProcess}
     * due to the various semantics which may be desired; this may change, but if entities have specific requirements for restart,
     * developers should either subclass the {@link SoftwareProcessDriverLifecycleEffectorTasks} and/or lean on sensors from the parent */
    enum ChildStartableMode {
        /** do nothing with {@link Startable} children */
        NONE(true, false, false),
        /** start (stop) {@link Startable} children concurrent with *driver* start (stop),
         * in foreground, so invoking entity will wait for children to complete.
         * <p>
         * if the child requires the parent to reach a particular state before acting,
         * when running in foreground the parent should communicate its state using sensors
         * which the child listens for.
         * note that often sensors at the parent are not activated until it is started,
         * so the usual sensors connected at an entity may not be available when running in this mode */
        FOREGROUND(false, false, false),
        /** as {@link #FOREGROUND} but {@link ChildStartableMode#isLate} */
        FOREGROUND_LATE(false, false, true),
        /** start {@link Startable} children concurrent with *driver* start (stop, restart),
         * but in background, ie disassociated from the effector task at this entity
         * (so that this entity can complete start/stop independent of children) */
        BACKGROUND(false, true, false),
        /** as {@link #BACKGROUND} but {@link ChildStartableMode#isLate} */
        BACKGROUND_LATE(false, true, true);

        /** whether starting (stopping, restarting) children is disabled */
        public final boolean isDisabled;
        /** whether starting (stopping, restarting) children is backgrounded, so parent should not wait on them */
        public final boolean isBackground;
        /** whether starting (stopping, restarting) children should be nested, so start occurs after the driver is started,
         * and stop before the driver is stopped (if false the children operations are concurrent with the parent),
         * (with restart always being done in parallel though this behaviour may change) */
        public final boolean isLate;

        private ChildStartableMode(boolean isDisabled, boolean isBackground, boolean isLate) {
            this.isDisabled = isDisabled;
            this.isBackground = isBackground;
            this.isLate = isLate;
        }

    }

    @SetFromFlag("childStartMode")
    ConfigKey<ChildStartableMode> CHILDREN_STARTABLE_MODE = ConfigKeys.newConfigKey(ChildStartableMode.class, "children.startable.mode");

    @SuppressWarnings("rawtypes")
    AttributeSensor<MachineProvisioningLocation> PROVISIONING_LOCATION = Sensors.newSensor(
            MachineProvisioningLocation.class, "softwareservice.provisioningLocation", "Location used to provision a machine where this is running");

    AttributeSensor<Boolean> SERVICE_PROCESS_IS_RUNNING = Sensors.newBooleanSensor("service.process.isRunning", 
            "Whether the process for the service is confirmed as running");
    
    AttributeSensor<Lifecycle> SERVICE_STATE_ACTUAL = Attributes.SERVICE_STATE_ACTUAL;
 
    AttributeSensor<String> PID_FILE = Sensors.newStringSensor("softwareprocess.pid.file", "PID file");

    @Beta
    public static class RestartSoftwareParameters {
        @Beta /** @since 0.7.0 semantics of parameters to restart being explored */
        public static final ConfigKey<Boolean> RESTART_CHILDREN = ConfigKeys.newConfigKey(Boolean.class, "restartChildren",
            "Whether to restart children; default false", false);

        @Beta /** @since 0.7.0 semantics of parameters to restart being explored */
        public static final ConfigKey<Object> RESTART_MACHINE = ConfigKeys.newConfigKey(Object.class, "restartMachine",
            "Whether to restart/replace the machine provisioned for this entity:  'true', 'false', or 'auto' are supported, "
            + "with the default being 'auto' which means to restart or reprovision the machine if there is no simpler way known to restart the entity "
            + "(for example, if the machine is unhealthy, it would not be possible to restart the process, not even via a stop-then-start sequence); "
            + "if the machine was not provisioned for this entity, this parameter has no effect", 
            RestartMachineMode.AUTO.toString().toLowerCase());
        
        // we supply a typed variant for retrieval; we want the untyped (above) to use lower case as the default in the GUI
        // (very hard if using enum, since enum takes the name, and RendererHints do not apply to parameters) 
        @Beta /** @since 0.7.0 semantics of parameters to restart being explored */
        public static final ConfigKey<RestartMachineMode> RESTART_MACHINE_TYPED = ConfigKeys.newConfigKey(RestartMachineMode.class, "restartMachine");
            
        public enum RestartMachineMode { TRUE, FALSE, AUTO }
    }

    @Beta
    public static class StopSoftwareParameters {
        @Beta /** @since 0.7.0 semantics of parameters to restart being explored */
        public static final ConfigKey<Boolean> STOP_MACHINE = ConfigKeys.newBooleanConfigKey("stopMachine",
                "Whether to stop the machine provisioned for this entity:  'true', or 'false' are supported, "
                        + "with the default being 'true'", true);
    }
    
    // NB: the START, STOP, and RESTART effectors themselves are (re)defined by MachineLifecycleEffectorTasks

}
