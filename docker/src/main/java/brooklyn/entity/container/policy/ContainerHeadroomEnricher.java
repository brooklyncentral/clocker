package brooklyn.entity.container.policy;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.location.docker.strategy.DockerAwarePlacementStrategy;
import brooklyn.location.docker.strategy.MaxContainersPlacementStrategy;
import brooklyn.policy.autoscaling.AutoScalerPolicy;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class ContainerHeadroomEnricher extends AbstractEnricher {

    private static final Logger LOG = LoggerFactory.getLogger(ContainerHeadroomEnricher.class);

    @SetFromFlag("headroom")
    public static final ConfigKey<Integer> CONTAINER_HEADROOM = ConfigKeys.newIntegerConfigKey(
            "docker.container.cluster.headroom",
            "Required headroom (free containers) for the Docker cluster");

    public void setEntity(EntityLocal entity) {
        Preconditions.checkArgument(entity instanceof DockerInfrastructure, "Entity must be a DockerInfrastructure: %s", entity);
        Preconditions.checkNotNull(getConfig(CONTAINER_HEADROOM), "Headroom must be configured for this enricher");

        super.setEntity(entity);

        subscribe(entity, DockerInfrastructure.DOCKER_CONTAINER_COUNT, new Listener());
        subscribe(entity, DockerInfrastructure.DOCKER_HOST_COUNT, new Listener());
    }

    private class Listener implements SensorEventListener<Object> {
        @Override
        public void onEvent(SensorEvent<Object> event) {
            recalculate();
        }
    }

    private void recalculate() {
        Integer maxContainers = null;
        List<DockerAwarePlacementStrategy> strategies = entity.getConfig(DockerInfrastructure.PLACEMENT_STRATEGIES);
        Optional<DockerAwarePlacementStrategy> lookup = Iterables.tryFind(strategies, Predicates.instanceOf(MaxContainersPlacementStrategy.class));
        if (lookup.isPresent()) {
            maxContainers = ((MaxContainersPlacementStrategy) lookup.get()).getConfig(MaxContainersPlacementStrategy.DOCKER_CONTAINER_CLUSTER_MAX_SIZE);
        }
        if (maxContainers == null) {
            maxContainers = entity.getConfig(MaxContainersPlacementStrategy.DOCKER_CONTAINER_CLUSTER_MAX_SIZE);
        }
        if (maxContainers == null) {
            maxContainers = MaxContainersPlacementStrategy.DEFAULT_MAX_CONTAINERS;
        }

        Integer containers = entity.getAttribute(DockerInfrastructure.DOCKER_CONTAINER_COUNT);
        Integer hosts = entity.getAttribute(DockerInfrastructure.DOCKER_HOST_COUNT);
        if (containers == null || hosts == null) return;
        int possible = maxContainers * hosts;
        int available = possible - containers;

        Integer headroom = getConfig(CONTAINER_HEADROOM);
        int needed = headroom - available;
        if (LOG.isDebugEnabled()) {
            LOG.debug("Headroom enricher: {} containers on {} hosts, {} available and {} needed",
                    new Object[] { containers, hosts, available, needed });
        }

        Map<String, Object> properties = ImmutableMap.<String,Object>of(
                AutoScalerPolicy.POOL_CURRENT_SIZE_KEY, hosts,
                AutoScalerPolicy.POOL_CURRENT_WORKRATE_KEY, containers,
                AutoScalerPolicy.POOL_LOW_THRESHOLD_KEY, maxContainers,
                AutoScalerPolicy.POOL_HIGH_THRESHOLD_KEY, possible);
        // Emit pool hot sensor if we need more containers, otherwise OK
        if (needed > 0 ) {
            emit(AutoScalerPolicy.DEFAULT_POOL_HOT_SENSOR, properties);
        } else {
            emit(AutoScalerPolicy.DEFAULT_POOL_OK_SENSOR, properties);
        }
    }
}
