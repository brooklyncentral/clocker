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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.InputStream;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.location.Location;
import brooklyn.util.ResourceUtils;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Strings;
import brooklyn.util.text.TemplateProcessor;

import com.google.common.annotations.Beta;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

/**
 * An abstract implementation of the {@link SoftwareProcessDriver}.
 */
public abstract class AbstractSoftwareProcessDriver implements SoftwareProcessDriver {

    private static final Logger log = LoggerFactory.getLogger(AbstractSoftwareProcessDriver.class);

    protected final EntityLocal entity;
    protected final ResourceUtils resource;
    protected final Location location;

    public AbstractSoftwareProcessDriver(EntityLocal entity, Location location) {
        this.entity = checkNotNull(entity, "entity");
        this.location = checkNotNull(location, "location");
        this.resource = ResourceUtils.create(entity);
    }

    /*
     * (non-Javadoc)
     * @see brooklyn.entity.basic.SoftwareProcessDriver#rebind()
     */
    @Override
    public void rebind() {
        // no-op
    }

    /**
     * Start the entity.
     * <p>
     * This installs, configures and launches the application process. However,
     * users can also call the {@link #install()}, {@link #customize()} and
     * {@link #launch()} steps independently. The {@link #postLaunch()} will
     * be called after the {@link #launch()} metheod is executed, but the
     * process may not be completely initialised at this stage, so care is
     * required when implementing these stages.
     * <p>
     * The {@link BrooklynConfigKeys#ENTITY_RUNNING} key can be set on the location
     * or the entity to skip the startup process if the entity is already running,
     * according to the {@link #isRunning()} method. To force the startup to be
     * skipped, {@link BrooklynConfigKeys#ENTITY_STARTED} can be set on the entity.
     * The {@link BrooklynConfigKeys#SKIP_INSTALLATION} key can also be used to
     * skip the {@link #setup()}, {@link #copyInstallResources()} and
     * {@link #install()} methods if set on the entity or location. 
     *
     * @see #stop()
     */
    @Override
    public void start() {
        boolean skipStart = false;
        Optional<Boolean> locationRunning = Optional.fromNullable(getLocation().getConfig(BrooklynConfigKeys.ENTITY_RUNNING));
        Optional<Boolean> entityRunning = Optional.fromNullable(entity.getConfig(BrooklynConfigKeys.ENTITY_RUNNING));
        Optional<Boolean> entityStarted = Optional.fromNullable(entity.getConfig(BrooklynConfigKeys.ENTITY_STARTED));
        if (locationRunning.or(entityRunning).or(false)) {
            skipStart = isRunning();
        } else {
            skipStart = entityStarted.or(false);
        }
        if (!skipStart) {
            DynamicTasks.queue("pre-install", new Runnable() { public void run() {
                preInstall();
            }});

            if (Strings.isNonBlank(entity.getConfig(BrooklynConfigKeys.PRE_INSTALL_COMMAND))) {
                DynamicTasks.queue("pre-install-command", new Runnable() { public void run() {
                    runPreInstallCommand(entity.getConfig(BrooklynConfigKeys.PRE_INSTALL_COMMAND));
                }});
            };

            Optional<Boolean> locationInstalled = Optional.fromNullable(getLocation().getConfig(BrooklynConfigKeys.SKIP_INSTALLATION));
            Optional<Boolean> entityInstalled = Optional.fromNullable(entity.getConfig(BrooklynConfigKeys.SKIP_INSTALLATION));
            boolean skipInstall = locationInstalled.or(entityInstalled).or(false);
            if (!skipInstall) {
                DynamicTasks.queue("setup", new Runnable() { public void run() {
                    waitForConfigKey(BrooklynConfigKeys.SETUP_LATCH);
                    setup();
                }});

                DynamicTasks.queue("copy-install-resources", new Runnable() { public void run() {
                    waitForConfigKey(BrooklynConfigKeys.INSTALL_RESOURCES_LATCH);
                    copyInstallResources();
                }});

                DynamicTasks.queue("install", new Runnable() { public void run() {
                    waitForConfigKey(BrooklynConfigKeys.INSTALL_LATCH);
                    install();
                }});
            }

            if (Strings.isNonBlank(entity.getConfig(BrooklynConfigKeys.POST_INSTALL_COMMAND))) {
                DynamicTasks.queue("post-install-command", new Runnable() { public void run() {
                    runPostInstallCommand(entity.getConfig(BrooklynConfigKeys.POST_INSTALL_COMMAND));
                }});
            };

            DynamicTasks.queue("customize", new Runnable() { public void run() {
                waitForConfigKey(BrooklynConfigKeys.CUSTOMIZE_LATCH);
                customize();
            }});

            DynamicTasks.queue("copy-runtime-resources", new Runnable() { public void run() {
                waitForConfigKey(BrooklynConfigKeys.RUNTIME_RESOURCES_LATCH);
                copyRuntimeResources();
            }});

            if (Strings.isNonBlank(entity.getConfig(BrooklynConfigKeys.PRE_LAUNCH_COMMAND))) {
                DynamicTasks.queue("pre-launch-command", new Runnable() { public void run() {
                    runPreLaunchCommand(entity.getConfig(BrooklynConfigKeys.PRE_LAUNCH_COMMAND));
                }});
            };

            DynamicTasks.queue("launch", new Runnable() { public void run() {
                waitForConfigKey(BrooklynConfigKeys.LAUNCH_LATCH);
                launch();
            }});

            if (Strings.isNonBlank(entity.getConfig(BrooklynConfigKeys.POST_LAUNCH_COMMAND))) {
                DynamicTasks.queue("post-launch-command", new Runnable() { public void run() {
                    runPostLaunchCommand(entity.getConfig(BrooklynConfigKeys.POST_LAUNCH_COMMAND));
                }});
            };
        }

        DynamicTasks.queue("post-launch", new Runnable() { public void run() {
            postLaunch();
        }});
    }

    @Override
    public abstract void stop();

    /**
     * Implement this method in child classes to add some post-launch behavior
     */
    public void preInstall() {}

    public abstract void runPreInstallCommand(String command);
    public abstract void setup();
    public abstract void copyInstallResources();
    public abstract void install();
    public abstract void runPostInstallCommand(String command);
    public abstract void copyRuntimeResources();
    public abstract void customize();
    public abstract void runPreLaunchCommand(String command);
    public abstract void launch();
    public abstract void runPostLaunchCommand(String command);

    @Override
    public void kill() {
        stop();
    }

    /**
     * Implement this method in child classes to add some post-launch behavior
     */
    public void postLaunch() {}

    @Override
    public void restart() {
        DynamicTasks.queue("stop (best effort)", new Runnable() { public void run() {
            DynamicTasks.markInessential();
            boolean previouslyRunning = isRunning();
            try {
                ServiceStateLogic.setExpectedState(getEntity(), Lifecycle.STOPPING);
                stop();
            } catch (Exception e) {
                // queue a failed task so that there is visual indication that this task had a failure,
                // without interrupting the parent
                if (previouslyRunning) {
                    log.warn(getEntity() + " restart: stop failed, when was previously running (ignoring)", e);
                    DynamicTasks.queue(Tasks.fail("Primary job failure (when previously running)", e));
                } else {
                    log.debug(getEntity() + " restart: stop failed (but was not previously running, so not a surprise)", e);
                    DynamicTasks.queue(Tasks.fail("Primary job failure (when not previously running)", e));
                }
                // the above queued tasks will cause this task to be indicated as failed, with an indication of severity
            }
        }});

        if (doFullStartOnRestart()) {
            DynamicTasks.waitForLast();
            ServiceStateLogic.setExpectedState(getEntity(), Lifecycle.STARTING);
            start();
        } else {
            DynamicTasks.queue("launch", new Runnable() { public void run() {
                ServiceStateLogic.setExpectedState(getEntity(), Lifecycle.STARTING);
                launch();
            }});
            DynamicTasks.queue("post-launch", new Runnable() { public void run() {
                postLaunch();
            }});
        }
    }

    @Beta
    /** ideally restart() would take options, e.g. whether to do full start, skip installs, etc;
     * however in the absence here is a toggle - not sure how well it works;
     * default is false which is similar to previous behaviour (with some seemingly-obvious tidies),
     * meaning install and configure will NOT be done on restart. */
    protected boolean doFullStartOnRestart() {
        return false;
    }

    @Override
    public EntityLocal getEntity() { return entity; }

    @Override
    public Location getLocation() { return location; }

    public InputStream getResource(String url) {
        return resource.getResourceFromUrl(url);
    }

    public String getResourceAsString(String url) {
        return resource.getResourceAsString(url);
    }

    public String processTemplate(File templateConfigFile, Map<String,Object> extraSubstitutions) {
        return processTemplate(templateConfigFile.toURI().toASCIIString(), extraSubstitutions);
    }

    public String processTemplate(File templateConfigFile) {
        return processTemplate(templateConfigFile.toURI().toASCIIString());
    }

    /** Takes the contents of a template file from the given URL (often a classpath://com/myco/myprod/myfile.conf or .sh)
     * and replaces "${entity.xxx}" with the result of entity.getXxx() and similar for other driver, location;
     * as well as replacing config keys on the management context
     * <p>
     * uses Freemarker templates under the covers
     **/
    public String processTemplate(String templateConfigUrl) {
        return processTemplate(templateConfigUrl, ImmutableMap.<String,String>of());
    }

    public String processTemplate(String templateConfigUrl, Map<String,? extends Object> extraSubstitutions) {
        return processTemplateContents(getResourceAsString(templateConfigUrl), extraSubstitutions);
    }

    public String processTemplateContents(String templateContents) {
        return processTemplateContents(templateContents, ImmutableMap.<String,String>of());
    }

    public String processTemplateContents(String templateContents, Map<String,? extends Object> extraSubstitutions) {
        return TemplateProcessor.processTemplateContents(templateContents, this, extraSubstitutions);
    }

    protected void waitForConfigKey(ConfigKey<?> configKey) {
        Object val = entity.getConfig(configKey);
        if (val != null) log.debug("{} finished waiting for {} (value {}); continuing...", new Object[] {this, configKey, val});
    }
}
