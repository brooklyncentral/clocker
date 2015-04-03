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
            "docker.container.cluster.headroom.count", "Required headroom (number of free containers) for the Docker cluster");

    @SetFromFlag("headroomPercent")
    public static final ConfigKey<Double> CONTAINER_HEADROOM_PERCENTAGE = ConfigKeys.newDoubleConfigKey(
            "docker.container.cluster.headroom.percent", "Required headroom (percentage free containers) for the Docker cluster");

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
    
    private BasicNotificationSensor lastPublished = null;

    public void setEntity(EntityLocal entity) {
        Preconditions.checkArgument(entity instanceof DockerInfrastructure, "Entity must be a DockerInfrastructure: %s", entity);
        super.setEntity(entity);

        Integer headroom = config().get(CONTAINER_HEADROOM);
        Double percent = config().get(CONTAINER_HEADROOM_PERCENTAGE);
        Preconditions.checkArgument((headroom != null) ^ (percent != null), "Headroom must be configured as either number or percentage for this enricher");
        if (headroom != null) {
            Preconditions.checkArgument(headroom > 0, "Headroom must be a positive integer: %d", headroom);
        }
        if (percent != null) {
            Preconditions.checkArgument(percent > 0d && percent < 1d, "Headroom percentage must be between 0.0 and 1.0: %f", percent);
        }

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
        List<DockerAwarePlacementStrategy> strategies = entity.config().get(DockerInfrastructure.PLACEMENT_STRATEGIES);
        Optional<DockerAwarePlacementStrategy> lookup = Iterables.tryFind(strategies, Predicates.instanceOf(MaxContainersPlacementStrategy.class));
        if (lookup.isPresent()) {
            maxContainers = ((MaxContainersPlacementStrategy) lookup.get()).config().get(MaxContainersPlacementStrategy.DOCKER_CONTAINER_CLUSTER_MAX_SIZE);
        }
        if (maxContainers == null) {
            maxContainers = entity.config().get(MaxContainersPlacementStrategy.DOCKER_CONTAINER_CLUSTER_MAX_SIZE);
        }
        if (maxContainers == null) {
            maxContainers = MaxContainersPlacementStrategy.DEFAULT_MAX_CONTAINERS;
        }

        // Calculate cluster state
        Integer containers = entity.getAttribute(DockerInfrastructure.DOCKER_CONTAINER_COUNT);
        Integer hosts = entity.getAttribute(DockerInfrastructure.DOCKER_HOST_COUNT);
        if (containers == null || hosts == null) return;
        int possible = maxContainers * hosts;
        int available = possible - containers;

        // Calculate headroom
        Integer headroom = config().get(CONTAINER_HEADROOM);
        Double percent = config().get(CONTAINER_HEADROOM_PERCENTAGE);
        if (headroom == null) {
            headroom = (int) Math.ceil(percent * possible);
        }

        // Calculate requirements
        int needed = headroom - available;
        double utilisation = (double) containers / (double) possible;
        if (LOG.isDebugEnabled()) {
            LOG.debug("Headroom enricher: {} containers on {} hosts, {} available and {} needed",
                    new Object[] { containers, hosts, available, needed });
        }

        double lowThreshold = (double) (possible - (headroom + maxContainers)) / (double) possible;
        lowThreshold = Math.max(0d, lowThreshold);
        
        double highThreshold = (double) (possible - headroom) / (double) possible;
        highThreshold = Math.max(0, highThreshold);
        
        // Emit current status of the pool as sensor data
        emit(CONTAINERS_NEEDED, needed);
        emit(DOCKER_CONTAINER_UTILISATION, utilisation);

        // Emit pool hot or cold sensors if headroom requirements not met
        Map<String, Object> properties = ImmutableMap.<String,Object>of(
                AutoScalerPolicy.POOL_CURRENT_SIZE_KEY, hosts,
                AutoScalerPolicy.POOL_CURRENT_WORKRATE_KEY, utilisation,
                AutoScalerPolicy.POOL_LOW_THRESHOLD_KEY, lowThreshold,
                AutoScalerPolicy.POOL_HIGH_THRESHOLD_KEY, highThreshold);
        if (needed > 0) {
            lastPublished = DOCKER_CONTAINER_CLUSTER_HOT;
            emit(DOCKER_CONTAINER_CLUSTER_HOT, properties);
        } else if (available > (headroom + maxContainers)) {
            lastPublished = DOCKER_CONTAINER_CLUSTER_COLD;
            emit(DOCKER_CONTAINER_CLUSTER_COLD, properties);
        } else {
            // Only emit ok if we weren't previously
            if (lastPublished != null && lastPublished != DOCKER_CONTAINER_CLUSTER_OK) {
                lastPublished = DOCKER_CONTAINER_CLUSTER_OK;
                emit(DOCKER_CONTAINER_CLUSTER_OK, properties);
            }
        }
    }
}
