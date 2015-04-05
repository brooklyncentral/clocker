/*
 * Copyright 2014-2015 by Cloudsoft Corporation Limited
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
package brooklyn.clocker;

import org.jclouds.compute.domain.OsFamily;

import brooklyn.catalog.Catalog;
import brooklyn.catalog.CatalogConfig;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.container.docker.DockerContainer;
import brooklyn.entity.container.docker.DockerHost;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.entity.container.policy.ContainerHeadroomEnricher;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.docker.strategy.BreadthFirstPlacementStrategy;
import brooklyn.location.docker.strategy.DockerAwarePlacementStrategy;
import brooklyn.location.docker.strategy.MaxContainersPlacementStrategy;
import brooklyn.location.jclouds.JcloudsLocationConfig;
import brooklyn.networking.sdn.SdnAttributes;
import brooklyn.networking.sdn.SdnProvider;
import brooklyn.networking.sdn.calico.CalicoNetwork;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Cidr;
import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableList;

/**
 * Brooklyn managed Docker cloud infrastructure.
 */
@Catalog(name="Clocker",
        description="Docker Cloud infrastructure with Calico networking",
        iconUrl="classpath://docker-top-logo.png")
public class CalicoDockerCloud extends AbstractApplication {

    @CatalogConfig(label="Docker Version", priority=100)
    public static final ConfigKey<String> DOCKER_VERSION = ConfigKeys.newConfigKeyWithDefault(DockerInfrastructure.DOCKER_VERSION, "1.5.0");

    @CatalogConfig(label="Calico Version", priority=90)
    public static final ConfigKey<String> CALICO_VERSION = ConfigKeys.newStringConfigKey("calico.version", "Calico SDN version", "0.2.0");

    @CatalogConfig(label="Etcd Version", priority=90)
    public static final ConfigKey<String> ETCD_VERSION = ConfigKeys.newStringConfigKey("etcd.version", "Etcd version", "2.0.5");

    @CatalogConfig(label="Location Name", priority=80)
    public static final ConfigKey<String> LOCATION_NAME = ConfigKeys.newConfigKeyWithDefault(DockerInfrastructure.LOCATION_NAME.getConfigKey(), "my-docker-cloud");

    @CatalogConfig(label="Host Cluster Minimum Size", priority=60)
    public static final ConfigKey<Integer> DOCKER_HOST_CLUSTER_MIN_SIZE = ConfigKeys.newConfigKeyWithDefault(DockerInfrastructure.DOCKER_HOST_CLUSTER_MIN_SIZE, 3);

    @CatalogConfig(label="Maximum Containers per Host", priority=50)
    public static final ConfigKey<Integer> DOCKER_CONTAINER_CLUSTER_MAX_SIZE = ConfigKeys.newConfigKeyWithDefault(MaxContainersPlacementStrategy.DOCKER_CONTAINER_CLUSTER_MAX_SIZE, 12);

    @CatalogConfig(label="Containers Headroom", priority=50)
    public static final ConfigKey<Integer> DOCKER_CONTAINER_CLUSTER_HEADROOM = ConfigKeys.newConfigKeyWithDefault(ContainerHeadroomEnricher.CONTAINER_HEADROOM, 4);

    @Override
    public void initApp() {
        MaxContainersPlacementStrategy maxContainers = new MaxContainersPlacementStrategy();
        maxContainers.injectManagementContext(getManagementContext());
        maxContainers.config().set(MaxContainersPlacementStrategy.DOCKER_CONTAINER_CLUSTER_MAX_SIZE, getConfig(DOCKER_CONTAINER_CLUSTER_MAX_SIZE));

        BreadthFirstPlacementStrategy breadthFirst = new BreadthFirstPlacementStrategy();
        breadthFirst.injectManagementContext(getManagementContext());

        // TODO We were hit by https://bugs.debian.org/cgi-bin/bugreport.cgi?bug=712691
        // when trying to open multiple ports on iptables (i.e. "iptables: Resource temporarily unavailable").
        // Possible fixes/workarounds are:
        //  1. Automatically upgrade iptables on the host.
        //  2. Include a retry.
        //  3. Turn off iptables, instead of enabling DockerInfrastructure.OPEN_IPTABLES on the DockerInfrastructure.
        // Currently we've gone for (3).

        addChild(EntitySpec.create(DockerInfrastructure.class)
                .configure(DockerInfrastructure.LOCATION_NAME, getConfig(LOCATION_NAME))
                .configure(DockerInfrastructure.DOCKER_VERSION, getConfig(DOCKER_VERSION))
                .configure(DockerInfrastructure.DOCKER_CERTIFICATE_PATH, "conf/server-cert.pem")
                .configure(DockerInfrastructure.DOCKER_KEY_PATH, "conf/server-key.pem")
                .configure(DockerInfrastructure.DOCKER_HOST_CLUSTER_MIN_SIZE, getConfig(DOCKER_HOST_CLUSTER_MIN_SIZE))
                .configure(ContainerHeadroomEnricher.CONTAINER_HEADROOM, getConfig(DOCKER_CONTAINER_CLUSTER_HEADROOM))
                .configure(DockerInfrastructure.HA_POLICY_ENABLE, false)
                .configure(DockerInfrastructure.PLACEMENT_STRATEGIES, ImmutableList.<DockerAwarePlacementStrategy>of(maxContainers, breadthFirst))
                .configure(SdnAttributes.SDN_ENABLE, true)
                .configure(DockerInfrastructure.SDN_PROVIDER_SPEC, EntitySpec.create(CalicoNetwork.class)
                        .configure(CalicoNetwork.CALICO_VERSION, getConfig(CALICO_VERSION))
                        .configure(CalicoNetwork.ETCD_VERSION, getConfig(ETCD_VERSION))
                        .configure(SdnProvider.CONTAINER_NETWORK_CIDR, Cidr.LINK_LOCAL)
                        .configure(SdnProvider.CONTAINER_NETWORK_SIZE, 24))
                .configure(DockerInfrastructure.DOCKER_HOST_SPEC, EntitySpec.create(DockerHost.class)
                        .configure(DockerHost.DOCKER_STORAGE_DRIVER, "overlay")
                        .configure(DockerHost.PROVISIONING_FLAGS, MutableMap.<String,Object>of(
                                JcloudsLocationConfig.MIN_CORES.getName(), 2,
                                JcloudsLocationConfig.MIN_RAM.getName(), 7000,
                                JcloudsLocationConfig.STOP_IPTABLES.getName(), true,
                                JcloudsLocationConfig.OS_FAMILY.getName(), OsFamily.UBUNTU,
                                JcloudsLocationConfig.OS_VERSION_REGEX.getName(), "14.04"))
                        .configure(SoftwareProcess.START_TIMEOUT, Duration.minutes(15))
                        .configure(DockerHost.DOCKER_HOST_NAME_FORMAT, "docker-host-%2$d")
                        .configure(DockerHost.DOCKER_CONTAINER_SPEC, EntitySpec.create(DockerContainer.class)
                                .configure(DockerContainer.DOCKER_CONTAINER_NAME_FORMAT, "container-%2$02x")))
                .displayName("Docker Cloud"));
    }
}
