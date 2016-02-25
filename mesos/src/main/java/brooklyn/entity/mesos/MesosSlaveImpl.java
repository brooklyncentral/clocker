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
package brooklyn.entity.mesos;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Functions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;

import org.apache.brooklyn.api.sensor.EnricherSpec;
import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.core.effector.ssh.SshEffectorTasks;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.feed.ConfigToAttributes;
import org.apache.brooklyn.enricher.stock.Enrichers;
import org.apache.brooklyn.entity.machine.MachineEntityImpl;
import org.apache.brooklyn.entity.software.base.AbstractSoftwareProcessSshDriver;
import org.apache.brooklyn.feed.http.HttpFeed;
import org.apache.brooklyn.feed.http.HttpPollConfig;
import org.apache.brooklyn.feed.http.HttpValueFunctions;
import org.apache.brooklyn.feed.http.JsonFunctions;
import org.apache.brooklyn.util.core.internal.ssh.SshTool;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.system.ProcessTaskWrapper;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Functionals;
import org.apache.brooklyn.util.math.MathFunctions;
import org.apache.brooklyn.util.text.ByteSizeStrings;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;

import brooklyn.networking.subnet.SubnetTier;

/**
 * Mesos slave machine implementation.
 */
public class MesosSlaveImpl extends MachineEntityImpl implements MesosSlave {

    private static final Logger LOG = LoggerFactory.getLogger(MesosSlave.class);

    private transient HttpFeed httpFeed;

    @Override
    public void init() {
        super.init();

        ConfigToAttributes.apply(this, MESOS_SLAVE_ID);

        EnricherSpec<?> serviceUp = Enrichers.builder()
                .propagating(ImmutableMap.of(SLAVE_ACTIVE, SERVICE_UP))
                .from(this)
                .build();
        enrichers().add(serviceUp);
    }

    @Override
    public void connectSensors() {
        super.connectSensors();

        final String id = sensors().get(MESOS_SLAVE_ID);

        HttpFeed.Builder httpFeedBuilder = HttpFeed.builder()
                .entity(this)
                .period(30, TimeUnit.SECONDS)
                .baseUri(getMesosCluster().sensors().get(Attributes.MAIN_URI))
                .credentialsIfNotNull(config().get(MesosCluster.MESOS_USERNAME), config().get(MesosCluster.MESOS_PASSWORD))
                .poll(HttpPollConfig.forSensor(MEMORY_AVAILABLE)
                        .suburl("/master/state.json")
                        .onSuccess(Functionals.chain(HttpValueFunctions.jsonContents(),
                                Functions.compose(
                                    MesosUtils.selectM(new Predicate<JsonElement>() {
                                        @Override
                                        public boolean apply(JsonElement input) {
                                            return input.getAsJsonObject().get("id").getAsString().equals(id);
                                        }}), JsonFunctions.walk("slaves")),
                                JsonFunctions.walkM("resources", "mem"),
                                JsonFunctions.castM(Long.class)))
                        .onFailureOrException(Functions.constant(-1L)))
                .poll(HttpPollConfig.forSensor(CPU_AVAILABLE)
                        .suburl("/master/state.json")
                        .onSuccess(Functionals.chain(HttpValueFunctions.jsonContents(),
                                Functions.compose(
                                    MesosUtils.selectM(new Predicate<JsonElement>() {
                                        @Override
                                        public boolean apply(JsonElement input) {
                                            return input.getAsJsonObject().get("id").getAsString().equals(id);
                                        }}), JsonFunctions.walk("slaves")),
                                JsonFunctions.walkM("resources", "cpus"),
                                JsonFunctions.castM(Double.class)))
                        .onFailureOrException(Functions.constant(-1d)))
                .poll(HttpPollConfig.forSensor(DISK_AVAILABLE)
                        .suburl("/master/state.json")
                        .onSuccess(Functionals.chain(HttpValueFunctions.jsonContents(),
                                Functions.compose(
                                    MesosUtils.selectM(new Predicate<JsonElement>() {
                                        @Override
                                        public boolean apply(JsonElement input) {
                                            return input.getAsJsonObject().get("id").getAsString().equals(id);
                                        }}), JsonFunctions.walk("slaves")),
                                JsonFunctions.walkM("resources", "disk"),
                                JsonFunctions.castM(Long.class)))
                        .onFailureOrException(Functions.constant(-1L)))
                .poll(HttpPollConfig.forSensor(MEMORY_USED)
                        .suburl("/master/state.json")
                        .onSuccess(Functionals.chain(HttpValueFunctions.jsonContents(),
                                Functions.compose(
                                    MesosUtils.selectM(new Predicate<JsonElement>() {
                                        @Override
                                        public boolean apply(JsonElement input) {
                                            return input.getAsJsonObject().get("id").getAsString().equals(id);
                                        }}), JsonFunctions.walk("slaves")),
                                JsonFunctions.walkM("used_resources", "mem"),
                                JsonFunctions.castM(Long.class)))
                        .onFailureOrException(Functions.constant(-1L)))
                .poll(HttpPollConfig.forSensor(CPU_USED)
                        .suburl("/master/state.json")
                        .onSuccess(Functionals.chain(HttpValueFunctions.jsonContents(),
                                Functions.compose(
                                    MesosUtils.selectM(new Predicate<JsonElement>() {
                                        @Override
                                        public boolean apply(JsonElement input) {
                                            return input.getAsJsonObject().get("id").getAsString().equals(id);
                                        }}), JsonFunctions.walk("slaves")),
                                JsonFunctions.walkM("used_resources", "cpus"),
                                JsonFunctions.castM(Double.class)))
                        .onFailureOrException(Functions.constant(-1d)))
                .poll(HttpPollConfig.forSensor(DISK_USED)
                        .suburl("/master/state.json")
                        .onSuccess(Functionals.chain(HttpValueFunctions.jsonContents(),
                                Functions.compose(
                                    MesosUtils.selectM(new Predicate<JsonElement>() {
                                        @Override
                                        public boolean apply(JsonElement input) {
                                            return input.getAsJsonObject().get("id").getAsString().equals(id);
                                        }}), JsonFunctions.walk("slaves")),
                                JsonFunctions.walkM("used_resources", "disk"),
                                JsonFunctions.castM(Long.class)))
                        .onFailureOrException(Functions.constant(-1L)));
        httpFeed = httpFeedBuilder.build();
    }

    @Override
    public void disconnectSensors() {
        if (httpFeed != null && httpFeed.isActivated()) httpFeed.destroy();

        super.disconnectSensors();
    }

    // TODO anything further really requires SSH authentication
    @Override
    protected void connectServiceUpIsRunning() {
        ServiceStateLogic.setExpectedState(this, Lifecycle.RUNNING);
    }

    @Override
    public void waitForEntityStart() {
        Entities.waitForServiceUp(this);
    }

    @Override
    public MesosCluster getMesosCluster() {
        return (MesosCluster) sensors().get(MESOS_CLUSTER);
    }

    @Override
    public SubnetTier getSubnetTier() {
        return sensors().get(SUBNET_TIER);
    }

    @Override
    public String execCommandTimeout(String command, Duration timeout) {
        ProcessTaskWrapper<String> task = SshEffectorTasks.ssh(command)
                .environmentVariables(((AbstractSoftwareProcessSshDriver) getDriver()).getShellEnvironment())
                .configure(SshTool.PROP_ALLOCATE_PTY, true) // TODO configure globally
                .requiringZeroAndReturningStdout()
                .machine(getMachine())
                .summary(command)
                .newTask();

        try {
            String result = DynamicTasks.queueIfPossible(task)
                    .executionContext(this)
                    .orSubmitAsync()
                    .asTask()
                    .get(timeout);
            return result;
        } catch (TimeoutException te) {
            throw new IllegalStateException("Timed out running command: " + command);
        } catch (Exception e) {
            Integer exitCode = task.getExitCode();
            LOG.warn("Command failed, return code {}: {}", exitCode == null ? -1 : exitCode, task.getStderr());
            throw Exceptions.propagate(e);
        }
    }

    static {
        RendererHints.register(REGISTERED_AT, RendererHints.displayValue(Time.toDateString()));

        RendererHints.register(MEMORY_AVAILABLE, RendererHints.displayValue(Functionals.chain(MathFunctions.times(1024L * 1024L), ByteSizeStrings.iso())));
        RendererHints.register(MEMORY_USED, RendererHints.displayValue(Functionals.chain(MathFunctions.times(1024L * 1024L), ByteSizeStrings.iso())));
        RendererHints.register(DISK_AVAILABLE, RendererHints.displayValue(Functionals.chain(MathFunctions.times(1024L * 1024L), ByteSizeStrings.iso())));
        RendererHints.register(DISK_USED, RendererHints.displayValue(Functionals.chain(MathFunctions.times(1024L * 1024L), ByteSizeStrings.iso())));
    }

}
