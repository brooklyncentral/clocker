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
package org.apache.brooklyn.entity.nosql.etcd;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.entity.EntityPredicates;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic.ServiceNotUpLogic;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.feed.ConfigToAttributes;
import org.apache.brooklyn.core.location.Locations;
import org.apache.brooklyn.core.sensor.DependentConfiguration;
import org.apache.brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import org.apache.brooklyn.entity.group.Cluster;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.entity.group.DynamicClusterImpl;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;

public class EtcdClusterImpl extends DynamicClusterImpl implements EtcdCluster {

    private static final Logger log = LoggerFactory.getLogger(EtcdClusterImpl.class);

    private transient Object memberMutex = new Object[0]; // For cluster membership management
    private transient Object clusterMutex = new Object[0]; // For cluster join/leave operations

    @Override
    public Object getClusterMutex() { return clusterMutex; }

    @Override
    public String getIconUrl() { return "https://s3.amazonaws.com/cloud.ohloh.net/attachments/85177/etcd-glyph-color_med.png"; }

    public void init() {
        super.init();

        sensors().set(NODE_ID, new AtomicInteger(0));
        ConfigToAttributes.apply(this, ETCD_NODE_SPEC);
        config().set(MEMBER_SPEC, sensors().get(ETCD_NODE_SPEC));
    }

    @Override
    public void start(Collection<? extends Location> locs) {
        addLocations(locs);
        List<Location> locations = MutableList.copyOf(Locations.getLocationsCheckingAncestors(locs, this));

        ServiceStateLogic.setExpectedState(this, Lifecycle.STARTING);

        connectSensors();

        super.start(locations);

        Optional<Entity> anyNode = Iterables.tryFind(getMembers(),Predicates.and(
                Predicates.instanceOf(EtcdNode.class),
                EntityPredicates.attributeEqualTo(EtcdNode.ETCD_NODE_HAS_JOINED_CLUSTER, true),
                EntityPredicates.attributeEqualTo(Startable.SERVICE_UP, true)));
        if (config().get(Cluster.INITIAL_SIZE) == 0 || anyNode.isPresent()) {
            sensors().set(Startable.SERVICE_UP, true);
            ServiceStateLogic.setExpectedState(this, Lifecycle.RUNNING);
        } else {
            log.warn("No Etcd nodes are found on the cluster: {}. Initialization Failed", getId());
            ServiceStateLogic.setExpectedState(this, Lifecycle.ON_FIRE);
        }
    }

    protected void connectSensors() {
        policies().add(PolicySpec.create(MemberTrackingPolicy.class)
                .displayName("EtcdCluster node tracker")
                .configure("sensorsToTrack", ImmutableSet.of(Attributes.HOSTNAME))
                .configure("group", this));
    }

    public boolean isProxied() {
        String memberType = config().get(MEMBER_SPEC).getType().getSimpleName();
        return memberType.contains("Proxy");
    }

    protected void onServerPoolMemberChanged(Entity member) {
        synchronized (memberMutex) {
            log.debug("For {}, considering membership of {} which is in locations {}", new Object[]{ this, member, member.getLocations() });

            Map<Entity, String> nodes = sensors().get(ETCD_CLUSTER_NODES);
            if (belongsInServerPool(member)) {
                if (nodes == null) {
                    nodes = Maps.newLinkedHashMap();
                }
                String name = Preconditions.checkNotNull(getNodeName(member));

                // Wait until node has been installed
                DynamicTasks.queueIfPossible(DependentConfiguration.attributeWhenReady(member, EtcdNode.ETCD_NODE_INSTALLED))
                        .orSubmitAndBlock(this)
                        .andWaitForSuccess();

                // Check for first node in the cluster.
                Duration timeout = config().get(BrooklynConfigKeys.START_TIMEOUT);
                Entity firstNode = sensors().get(DynamicCluster.FIRST);
                if (member.equals(firstNode)) {
                    nodes.put(member, name);
                    recalculateClusterAddresses(nodes);
                    log.info("Adding first node {}: {}; {} to cluster", new Object[] { this, member, name });
                    ((EntityInternal) member).sensors().set(EtcdNode.ETCD_NODE_HAS_JOINED_CLUSTER, Boolean.TRUE);
                } else {
                    int retry = 3; // TODO use a configurable Repeater instead?
                    while (retry --> 0 && member.sensors().get(EtcdNode.ETCD_NODE_HAS_JOINED_CLUSTER) == null && !nodes.containsKey(member)) {
                        Optional<Entity> anyNodeInCluster = Iterables.tryFind(nodes.keySet(), Predicates.and(
                                Predicates.instanceOf(EtcdNode.class),
                                EntityPredicates.attributeEqualTo(EtcdNode.ETCD_NODE_HAS_JOINED_CLUSTER, Boolean.TRUE)));
                        if (anyNodeInCluster.isPresent()) {
                            DynamicTasks.queueIfPossible(DependentConfiguration.builder().attributeWhenReady(anyNodeInCluster.get(), Startable.SERVICE_UP).timeout(timeout).build())
                                    .orSubmitAndBlock(this)
                                    .andWaitForSuccess();
                            Entities.invokeEffectorWithArgs(this, anyNodeInCluster.get(), EtcdNode.JOIN_ETCD_CLUSTER, name, getNodeAddress(member)).blockUntilEnded(timeout);
                            nodes.put(member, name);
                            recalculateClusterAddresses(nodes);
                            log.info("Adding node {}: {}; {} to cluster", new Object[] { this, member, name });
                            ((EntityInternal) member).sensors().set(EtcdNode.ETCD_NODE_HAS_JOINED_CLUSTER, Boolean.TRUE);
                        } else {
                            log.info("Waiting for first node in cluster {}", this);
                            Time.sleep(Duration.seconds(15));
                        }
                    }
                }
            } else {
                if (nodes != null && nodes.containsKey(member)) {
                    Optional<Entity> anyNodeInCluster = Iterables.tryFind(nodes.keySet(), Predicates.and(
                            Predicates.instanceOf(EtcdNode.class),
                            EntityPredicates.attributeEqualTo(EtcdNode.ETCD_NODE_HAS_JOINED_CLUSTER, Boolean.TRUE),
                            Predicates.not(Predicates.equalTo(member))));
                    if (anyNodeInCluster.isPresent()) {
                        Entities.invokeEffectorWithArgs(this, anyNodeInCluster.get(), EtcdNode.LEAVE_ETCD_CLUSTER, getNodeName(member)).blockUntilEnded();
                    }
                    nodes.remove(member);
                    recalculateClusterAddresses(nodes);
                    log.info("Removing node {}: {}; {} from cluster", new Object[] { this, member, getNodeName(member) });
                    ((EntityInternal) member).sensors().set(EtcdNode.ETCD_NODE_HAS_JOINED_CLUSTER, Boolean.FALSE);
                }
            }

            ServiceNotUpLogic.updateNotUpIndicatorRequiringNonEmptyMap(this, ETCD_CLUSTER_NODES);
            log.debug("Done {} checkEntity {}", this, member);
        }
    }

    private void recalculateClusterAddresses(Map<Entity,String> nodes) {
        Map<String,String> addresses = Maps.newHashMap();
        for (Entity entity : nodes.keySet()) {
            if (entity instanceof EtcdNode) {
                addresses.put(getNodeName(entity), getNodeAddress(entity));
            }
        }
        sensors().set(ETCD_CLUSTER_NODES, nodes);
        sensors().set(NODE_LIST, Joiner.on(",").withKeyValueSeparator("=").join(addresses));
    }

    protected boolean belongsInServerPool(Entity member) {
        if (member.sensors().get(Attributes.HOSTNAME) == null) {
            log.debug("Members of {}, checking {}, eliminating because hostname not yet set", this, member);
            return false;
        }
        if (!getMembers().contains(member)) {
            log.debug("Members of {}, checking {}, eliminating because not member", this, member);
            return false;
        }
        log.debug("Members of {}, checking {}, approving", this, member);
        return true;
    }

    private String getNodeName(Entity node) {
        return node.sensors().get(EtcdNode.ETCD_NODE_NAME);
    }

    private String getNodeAddress(Entity node) {
        return "http://" + node.sensors().get(Attributes.SUBNET_ADDRESS) + ":" + node.sensors().get(EtcdNode.ETCD_PEER_PORT);
    }

    public static class MemberTrackingPolicy extends AbstractMembershipTrackingPolicy {
        @Override
        protected void onEntityEvent(EventType type, Entity target) {
            ((EtcdClusterImpl) entity).onServerPoolMemberChanged(target);
        }
    }
}
