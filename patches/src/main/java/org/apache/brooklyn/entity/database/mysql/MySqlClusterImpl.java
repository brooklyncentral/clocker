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
package org.apache.brooklyn.entity.database.mysql;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.entity.EntityPredicates;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic.ServiceNotUpLogic;
import org.apache.brooklyn.core.sensor.DependentConfiguration;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.enricher.stock.Enrichers;
import org.apache.brooklyn.entity.database.DatastoreMixins;
import org.apache.brooklyn.entity.group.DynamicClusterImpl;
import org.apache.brooklyn.feed.function.FunctionFeed;
import org.apache.brooklyn.feed.function.FunctionPollConfig;
import org.apache.brooklyn.util.collections.CollectionFunctionals;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.TaskBuilder;
import org.apache.brooklyn.util.guava.Functionals;
import org.apache.brooklyn.util.guava.IfFunctions;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.text.StringPredicates;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;

// https://dev.mysql.com/doc/refman/5.7/en/replication-howto.html

// TODO Bootstrap slave from dump for the case where the binary log is purged
// TODO Promote slave to master
// TODO SSL connection between master and slave
// TODO DB credentials littered all over the place in file system
public class MySqlClusterImpl extends DynamicClusterImpl implements MySqlCluster {
    private static final AttributeSensor<Boolean> NODE_REPLICATION_INITIALIZED = Sensors.newBooleanSensor("mysql.replication_initialized");

    private static final String MASTER_CONFIG_URL = "classpath:///org/apache/brooklyn/entity/database/mysql/mysql_master.conf";
    private static final String SLAVE_CONFIG_URL = "classpath:///org/apache/brooklyn/entity/database/mysql/mysql_slave.conf";
    private static final int MASTER_SERVER_ID = 1;
    private static final Predicate<Entity> IS_MASTER = EntityPredicates.configEqualTo(MySqlNode.MYSQL_SERVER_ID, MASTER_SERVER_ID);

    @SuppressWarnings("serial")
    private static final AttributeSensor<Supplier<Integer>> SLAVE_NEXT_SERVER_ID = Sensors.newSensor(new TypeToken<Supplier<Integer>>() {},
            "mysql.slave.next_server_id", "Returns the ID of the next slave server");
    @SuppressWarnings("serial")
    private static final AttributeSensor<Map<String, String>> SLAVE_ID_ADDRESS_MAPPING = Sensors.newSensor(new TypeToken<Map<String, String>>() {},
            "mysql.slave.id_address_mapping", "Maps slave entity IDs to SUBNET_ADDRESS, so the address is known at member remove time.");

    @Override
    public void init() {
        super.init();
        // Set id supplier in attribute so it is serialized
        setAttribute(SLAVE_NEXT_SERVER_ID, new NextServerIdSupplier());
        setAttribute(SLAVE_ID_ADDRESS_MAPPING, new ConcurrentHashMap<String, String>());
        if (getConfig(SLAVE_PASSWORD) == null) {
            setAttribute(SLAVE_PASSWORD, Identifiers.makeRandomId(8));
        } else {
            setAttribute(SLAVE_PASSWORD, getConfig(SLAVE_PASSWORD));
        }
        initSubscriptions();
    }

    @Override
    public void rebind() {
        super.rebind();
        initSubscriptions();
    }

    private void initSubscriptions() {
        subscribeToMembers(this, MySqlNode.SERVICE_PROCESS_IS_RUNNING, new NodeRunningListener(this));
        subscribe(this, MEMBER_REMOVED, new MemberRemovedListener());
    }

    @Override
    protected void initEnrichers() {
        super.initEnrichers();
        propagateMasterAttribute(MySqlNode.HOSTNAME);
        propagateMasterAttribute(MySqlNode.ADDRESS);
        propagateMasterAttribute(MySqlNode.SUBNET_HOSTNAME);
        propagateMasterAttribute(MySqlNode.SUBNET_ADDRESS);
        propagateMasterAttribute(MySqlNode.MYSQL_PORT);
        propagateMasterAttribute(MySqlNode.DATASTORE_URL);

        addEnricher(Enrichers.builder()
                .aggregating(MySqlNode.DATASTORE_URL)
                .publishing(SLAVE_DATASTORE_URL_LIST)
                .computing(Functions.<Collection<String>>identity())
                .entityFilter(Predicates.not(IS_MASTER))
                .fromMembers()
                .build());

        addEnricher(Enrichers.builder()
                .aggregating(MySqlNode.QUERIES_PER_SECOND_FROM_MYSQL)
                .publishing(QUERIES_PER_SECOND_FROM_MYSQL_PER_NODE)
                .fromMembers()
                .computingAverage()
                .defaultValueForUnreportedSensors(0d)
                .build());
    }

    private void propagateMasterAttribute(AttributeSensor<?> att) {
        addEnricher(Enrichers.builder()
                .aggregating(att)
                .publishing(att)
                .computing(IfFunctions.ifPredicate(CollectionFunctionals.notEmpty())
                        .apply(CollectionFunctionals.firstElement())
                        .defaultValue(null))
                .entityFilter(IS_MASTER)
                .build());
    }

    @Override
    protected EntitySpec<?> getFirstMemberSpec() {
        final EntitySpec<?> firstMemberSpec = super.getFirstMemberSpec();
        if (firstMemberSpec != null) {
            return applyDefaults(firstMemberSpec, Suppliers.ofInstance(MASTER_SERVER_ID), MASTER_CONFIG_URL);
        }

        final EntitySpec<?> memberSpec = super.getMemberSpec();
        if (memberSpec != null) {
            if (!isKeyConfigured(memberSpec, MySqlNode.TEMPLATE_CONFIGURATION_URL.getConfigKey())) {
                return EntitySpec.create(memberSpec)
                        .configure(MySqlNode.MYSQL_SERVER_ID, MASTER_SERVER_ID)
                        .configure(MySqlNode.TEMPLATE_CONFIGURATION_URL, MASTER_CONFIG_URL);
            } else {
                return memberSpec;
            }
        }

        return EntitySpec.create(MySqlNode.class)
                .displayName("MySql Master")
                .configure(MySqlNode.MYSQL_SERVER_ID, MASTER_SERVER_ID)
                .configure(MySqlNode.TEMPLATE_CONFIGURATION_URL, MASTER_CONFIG_URL);
    }

    @Override
    protected EntitySpec<?> getMemberSpec() {
        Supplier<Integer> serverIdSupplier = getAttribute(SLAVE_NEXT_SERVER_ID);

        EntitySpec<?> spec = super.getMemberSpec();
        if (spec != null) {
            return applyDefaults(spec, serverIdSupplier, SLAVE_CONFIG_URL);
        }

        return EntitySpec.create(MySqlNode.class)
                .displayName("MySql Slave")
                .configure(MySqlNode.MYSQL_SERVER_ID, serverIdSupplier.get())
                .configure(MySqlNode.TEMPLATE_CONFIGURATION_URL, SLAVE_CONFIG_URL);
    }

    private EntitySpec<?> applyDefaults(EntitySpec<?> spec, Supplier<Integer> serverId, String configUrl) {
        boolean needsServerId = !isKeyConfigured(spec, MySqlNode.MYSQL_SERVER_ID);
        boolean needsConfigUrl = !isKeyConfigured(spec, MySqlNode.TEMPLATE_CONFIGURATION_URL.getConfigKey());
        if (needsServerId || needsConfigUrl) {
            EntitySpec<?> clonedSpec = EntitySpec.create(spec);
            if (needsServerId) {
                clonedSpec.configure(MySqlNode.MYSQL_SERVER_ID, serverId.get());
            }
            if (needsConfigUrl) {
                clonedSpec.configure(MySqlNode.TEMPLATE_CONFIGURATION_URL, configUrl);
            }
            return clonedSpec;
        } else {
            return spec;
        }
    }

    private boolean isKeyConfigured(EntitySpec<?> spec, ConfigKey<?> key) {
        return spec.getConfig().containsKey(key) || spec.getFlags().containsKey(key.getName());
    }

    @Override
    protected Entity createNode(Location loc, Map<?, ?> flags) {
        Entity node = super.createNode(loc, flags);
        if (!IS_MASTER.apply(node)) {
            ServiceNotUpLogic.updateNotUpIndicator((EntityLocal)node, MySqlSlave.SLAVE_HEALTHY, "Replication not started");

            addFeed(FunctionFeed.builder()
                .entity((EntityLocal)node)
                .period(Duration.FIVE_SECONDS)
                .poll(FunctionPollConfig.forSensor(MySqlSlave.SLAVE_HEALTHY)
                        .callable(new SlaveStateCallable(node))
                        .checkSuccess(StringPredicates.isNonBlank())
                        .onSuccess(new SlaveStateParser(node))
                        .setOnFailure(false)
                        .description("Polls SHOW SLAVE STATUS"))
                .build());

            node.addEnricher(Enrichers.builder().updatingMap(Attributes.SERVICE_NOT_UP_INDICATORS)
                    .from(MySqlSlave.SLAVE_HEALTHY)
                    .computing(Functionals.ifNotEquals(true).value("Slave replication status is not healthy") )
                    .build());
        }
        return node;
    }

    public static class SlaveStateCallable implements Callable<String> {
        private Entity slave;
        public SlaveStateCallable(Entity slave) {
            this.slave = slave;
        }

        @Override
        public String call() throws Exception {
            if (Boolean.TRUE.equals(slave.getAttribute(MySqlNode.SERVICE_PROCESS_IS_RUNNING))) {
                return slave.invoke(MySqlNode.EXECUTE_SCRIPT, ImmutableMap.of("commands", "SHOW SLAVE STATUS \\G")).asTask().getUnchecked();
            } else {
                return null;
            }
        }

    }

    public static class SlaveStateParser implements Function<String, Boolean> {
        private Entity slave;

        public SlaveStateParser(Entity slave) {
            this.slave = slave;
        }

        @Override
        public Boolean apply(String result) {
            Map<String, String> status = MySqlRowParser.parseSingle(result);
            String secondsBehindMaster = status.get("Seconds_Behind_Master");
            if (secondsBehindMaster != null && !"NULL".equals(secondsBehindMaster)) {
                ((EntityLocal)slave).setAttribute(MySqlSlave.SLAVE_SECONDS_BEHIND_MASTER, new Integer(secondsBehindMaster));
            }
            return "Yes".equals(status.get("Slave_IO_Running")) && "Yes".equals(status.get("Slave_SQL_Running"));
        }

    }

    private static class NextServerIdSupplier implements Supplier<Integer> {
        private AtomicInteger nextId = new AtomicInteger(MASTER_SERVER_ID+1);

        @Override
        public Integer get() {
            return nextId.getAndIncrement();
        }
    }

    // ============= Member Init =============

    // The task is executed in inessential context (event handler) so
    // not visible in tasks UI. Better make it visible so the user can
    // see failures, currently accessible only from logs.
    private static final class InitReplicationTask implements Runnable {
        private final MySqlCluster cluster;
        private final MySqlNode node;

        private InitReplicationTask(MySqlCluster cluster, MySqlNode node) {
            this.cluster = cluster;
            this.node = node;
        }

        @Override
        public void run() {
            Integer serverId = node.getConfig(MySqlNode.MYSQL_SERVER_ID);
            if (serverId == MASTER_SERVER_ID) {
                initMaster(node);
            } else if (serverId > MASTER_SERVER_ID) {
                initSlave(node);
            }
        }

        private void initMaster(MySqlNode master) {
            String binLogInfo = executeScriptOnNode(master, "FLUSH TABLES WITH READ LOCK;SHOW MASTER STATUS \\G UNLOCK TABLES;");
            Map<String, String> status = MySqlRowParser.parseSingle(binLogInfo);
            String file = status.get("File");
            if (file != null) {
                ((EntityInternal)master).setAttribute(MySqlMaster.MASTER_LOG_FILE, file);
            }
            String position = status.get("Position");
            if (position != null) {
                ((EntityInternal)master).setAttribute(MySqlMaster.MASTER_LOG_POSITION, new Integer(position));
            }

            //NOTE: Will be executed on each start, analogously to the standard CREATION_SCRIPT config
            String creationScript = getDatabaseCreationScriptAsString(master);
            if (creationScript != null) {
                master.invoke(MySqlNode.EXECUTE_SCRIPT, ImmutableMap.of("commands", creationScript));
            }
        }

        @Nullable private static String getDatabaseCreationScriptAsString(Entity entity) {
            String url = entity.getConfig(MySqlMaster.MASTER_CREATION_SCRIPT_URL);
            if (!Strings.isBlank(url))
                return new ResourceUtils(entity).getResourceAsString(url);
            String contents = entity.getConfig(MySqlMaster.MASTER_CREATION_SCRIPT_CONTENTS);
            if (!Strings.isBlank(contents))
                return contents;
            return null;
        }
        
        private void initSlave(MySqlNode slave) {
            MySqlNode master = (MySqlNode) Iterables.find(cluster.getMembers(), IS_MASTER);
            String masterLogFile = validateSqlParam(getAttributeBlocking(master, MySqlMaster.MASTER_LOG_FILE));
            Integer masterLogPos = getAttributeBlocking(master, MySqlMaster.MASTER_LOG_POSITION);
            String masterAddress = validateSqlParam(master.getAttribute(MySqlNode.SUBNET_ADDRESS));
            Integer masterPort = master.getAttribute(MySqlNode.MYSQL_PORT);
            String slaveAddress = validateSqlParam(slave.getAttribute(MySqlNode.SUBNET_ADDRESS));
            String username = validateSqlParam(cluster.getConfig(SLAVE_USERNAME));
            String password = validateSqlParam(cluster.getAttribute(SLAVE_PASSWORD));

            executeScriptOnNode(master, String.format(
                    "CREATE USER '%s'@'%s' IDENTIFIED BY '%s';\n" +
                    "GRANT REPLICATION SLAVE ON *.* TO '%s'@'%s';\n",
                    username, slaveAddress, password, username, slaveAddress));

            String slaveCmd = String.format(
                    "CHANGE MASTER TO " +
                        "MASTER_HOST='%s', " +
                        "MASTER_PORT=%d, " +
                        "MASTER_USER='%s', " +
                        "MASTER_PASSWORD='%s', " +
                        "MASTER_LOG_FILE='%s', " +
                        "MASTER_LOG_POS=%d;\n" +
                    "START SLAVE;\n",
                    masterAddress, masterPort, username, password, masterLogFile, masterLogPos);
            executeScriptOnNode(slave, slaveCmd);

            cluster.getAttribute(SLAVE_ID_ADDRESS_MAPPING).put(slave.getId(), slave.getAttribute(MySqlNode.SUBNET_ADDRESS));
        }

        private <T> T getAttributeBlocking(Entity masterNode, AttributeSensor<T> att) {
            return DynamicTasks.queue(DependentConfiguration.attributeWhenReady(masterNode, att)).getUnchecked();
        }

    }

    private static final class NodeRunningListener implements SensorEventListener<Boolean> {
        private MySqlCluster cluster;

        public NodeRunningListener(MySqlCluster cluster) {
            this.cluster = cluster;
        }

        @Override
        public void onEvent(SensorEvent<Boolean> event) {
            final MySqlNode node = (MySqlNode) event.getSource();
            if (Boolean.TRUE.equals(event.getValue()) &&
                    // We are interested in SERVICE_PROCESS_IS_RUNNING only while haven't come online yet.
                    // Probably will get several updates while replication is initialized so an additional
                    // check is needed whether we have already seen this.
                    Boolean.FALSE.equals(node.getAttribute(MySqlNode.SERVICE_UP)) &&
                    !Boolean.TRUE.equals(node.getAttribute(NODE_REPLICATION_INITIALIZED))) {

                // Events executed sequentially so no need to synchronize here.
                ((EntityLocal)node).setAttribute(NODE_REPLICATION_INITIALIZED, Boolean.TRUE);

                DynamicTasks.queueIfPossible(TaskBuilder.builder()
                        .displayName("Configure master-slave replication on node")
                        .body(new InitReplicationTask(cluster, node))
                        .build())
                    .orSubmitAsync(node);
            }
        }

    }

    // ============= Member Remove =============

    public class MemberRemovedListener implements SensorEventListener<Entity> {
        @Override
        public void onEvent(SensorEvent<Entity> event) {
            MySqlCluster cluster = (MySqlCluster) event.getSource();
            Entity node = event.getValue();
            String slaveAddress = cluster.getAttribute(SLAVE_ID_ADDRESS_MAPPING).remove(node.getId());
            if (slaveAddress != null) {
                DynamicTasks.queueIfPossible(TaskBuilder.builder()
                        .displayName("Remove slave access")
                        .body(new RemoveSlaveConfigTask(cluster, slaveAddress))
                        .build())
                    .orSubmitAsync(cluster);
            }
        }
    }

    public class RemoveSlaveConfigTask implements Runnable {
        private MySqlCluster cluster;
        private String slaveAddress;

        public RemoveSlaveConfigTask(MySqlCluster cluster, String slaveAddress) {
            this.cluster = cluster;
            this.slaveAddress = validateSqlParam(slaveAddress);
        }

        @Override
        public void run() {
            // Could already be gone if stopping the entire app - let it throw an exception
            MySqlNode master = (MySqlNode) Iterables.find(cluster.getMembers(), IS_MASTER);
            String username = validateSqlParam(cluster.getConfig(SLAVE_USERNAME));
            executeScriptOnNode(master, String.format("DROP USER '%s'@'%s';", username, slaveAddress));
        }

    }

    // Can't call node.executeScript directly, need to change execution context, so use an effector task
    private static String executeScriptOnNode(MySqlNode node, String commands) {
        return node.invoke(MySqlNode.EXECUTE_SCRIPT, ImmutableMap.of(MySqlNode.EXECUTE_SCRIPT_COMMANDS, commands)).getUnchecked();
    }

    private static String validateSqlParam(String config) {
        // Don't go into escape madness, just deny any suspicious strings.
        // Would be nice to use prepared statements, but not worth pulling in the extra dependencies.
        if (config.contains("'") && config.contains("\\")) {
            throw new IllegalStateException("User provided string contains illegal SQL characters: " + config);
        }
        return config;
    }

}
