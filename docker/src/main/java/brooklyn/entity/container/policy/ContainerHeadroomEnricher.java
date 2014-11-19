package brooklyn.entity.container.policy;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.render.RendererHints;
import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.location.docker.strategy.DockerAwarePlacementStrategy;
import brooklyn.location.docker.strategy.MaxContainersPlacementStrategy;
import brooklyn.policy.Enricher;
import brooklyn.policy.autoscaling.AutoScalerPolicy;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.math.MathFunctions;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

/**
 * An {@link Enricher} that emits {@link #DOCKER_CONTAINER_CLUSTER_HOT hot} or {@link #DOCKER_CONTAINER_CLUSTER_COLD cold}
 * notifications based on the {@link #DOCKER_CONTAINER_UTILISATION container utilisation} and
 * {@link #CONTAINER_HEADROOM headroom} requirements.
 * <p>
 * This enricher must be applied to the {@link DockerInfrastructure} entity, and is normally created during the
 * entity {@link AbstractEntity#init() initialisation} along with an {@link AutoScalerPolicy}. Notifications
 * are fed into the policy, which resizes the child Docker host cluster. Workrates for the policy are calculated
 * as resource utilisation percentages, based on the configured maximum number of containers per host. This is
 * obtained from the {@link MaxContainersPlacementStrategy} or through the
 * {@link MaxContainersPlacementStrategy#DOCKER_CONTAINER_CLUSTER_MAX_SIZE maxContainers} configuration on the
 * infrastructure entity. Workrate thresholds are calculated based on cluster utilisation with the specific headroom
 * available, and the cluster will be resized as appropriate.
 */
public class ContainerHeadroomEnricher extends AbstractEnricher {

    private static final Logger LOG = LoggerFactory.getLogger(ContainerHeadroomEnricher.class);

    @SetFromFlag("headroom")
    public static final ConfigKey<Integer> CONTAINER_HEADROOM = ConfigKeys.newIntegerConfigKey(
            "docker.container.cluster.headroom", "Required headroom (free containers) for the Docker cluster");

    public static final AttributeSensor<Integer> CONTAINERS_NEEDED = Sensors.newIntegerSensor(
            "docker.container.cluster.needed", "Number of containers needed to give requierd headroom");
    public static final AttributeSensor<Double> DOCKER_CONTAINER_UTILISATION = Sensors.newDoubleSensor(
            "docker.container.cluster.utilisation", "Resource utilisation of Docker container cluster");

    public static final BasicNotificationSensor<Map> DOCKER_CONTAINER_CLUSTER_HOT = new BasicNotificationSensor<Map>(
            Map.class, "docker.container.cluster.hot", "Docker cluster has insufficient containers for current workload");
    public static final BasicNotificationSensor<Map> DOCKER_CONTAINER_CLUSTER_COLD = new BasicNotificationSensor<Map>(
            Map.class, "docker.container.cluster.cold", "Docker cluster has too many containers for current workload");
    public static final BasicNotificationSensor<Map> DOCKER_CONTAINER_CLUSTER_OK = new BasicNotificationSensor<Map>(
            Map.class, "docker.container.cluster.ok", "Docker cluster is ok for the current workload");

    static {
        RendererHints.register(DOCKER_CONTAINER_UTILISATION, RendererHints.displayValue(MathFunctions.percent(3)));
    }

    public void setEntity(EntityLocal entity) {
        Preconditions.checkArgument(entity instanceof DockerInfrastructure, "Entity must be a DockerInfrastructure: %s", entity);
        Preconditions.checkNotNull(getConfig(CONTAINER_HEADROOM), "Headroom must be configured for this enricher");
        Preconditions.checkArgument(getConfig(CONTAINER_HEADROOM) > 0, "Headroom must be a positive integer: %d", getConfig(CONTAINER_HEADROOM));

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

        // Calculate headroom
        Integer headroom = getConfig(CONTAINER_HEADROOM);
        int needed = headroom - available;
        double utilisation = (double) containers / (double) possible;
        if (LOG.isDebugEnabled()) {
            LOG.debug("Headroom enricher: {} containers on {} hosts, {} available and {} needed",
                    new Object[] { containers, hosts, available, needed });
        }

        // Emit current status of the pool as sensor data
        emit(CONTAINERS_NEEDED, needed);
        emit(DOCKER_CONTAINER_UTILISATION, utilisation);

        // Emit pool hot or cold sensors if headroom requirements not met
        Map<String, Object> properties = ImmutableMap.<String,Object>of(
                AutoScalerPolicy.POOL_CURRENT_SIZE_KEY, hosts,
                AutoScalerPolicy.POOL_CURRENT_WORKRATE_KEY, utilisation,
                AutoScalerPolicy.POOL_LOW_THRESHOLD_KEY, Math.max(0d, (double) (possible - (headroom + maxContainers)) / (double) possible),
                AutoScalerPolicy.POOL_HIGH_THRESHOLD_KEY, Math.max(utilisation, (double) (possible - headroom) / (double) possible));
        if (needed > 0 ) {
            emit(DOCKER_CONTAINER_CLUSTER_HOT, properties);
        } else if (available > (headroom + maxContainers)) {
            emit(DOCKER_CONTAINER_CLUSTER_COLD, properties);
        } else {
            emit(DOCKER_CONTAINER_CLUSTER_OK, properties);
        }
    }
}
