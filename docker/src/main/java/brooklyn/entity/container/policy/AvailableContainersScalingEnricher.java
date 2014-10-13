package brooklyn.entity.container.policy;

import java.util.Map;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;

import brooklyn.config.ConfigKey;
import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.policy.autoscaling.AutoScalerPolicy;

public class AvailableContainersScalingEnricher extends AbstractEnricher {

    ConfigKey<ConfigKey<Integer>> MAX_CONTAINERS = ConfigKeys.newConfigKey(new TypeToken<ConfigKey<Integer>>() {},
            "enricher.availability.containers", "Sensor producing the number of containers in the cluster",
            DockerInfrastructure.DOCKER_CONTAINER_CLUSTER_MAX_SIZE);

    ConfigKey<AttributeSensor<Integer>> CONTAINERS = ConfigKeys.newConfigKey(new TypeToken<AttributeSensor<Integer>>() {},
            "enricher.availability.containers", "Sensor producing the number of containers in the cluster",
            DockerInfrastructure.DOCKER_CONTAINER_COUNT);

    ConfigKey<AttributeSensor<Integer>> HOSTS = ConfigKeys.newConfigKey(new TypeToken<AttributeSensor<Integer>>() {},
            "enricher.availability.hosts", "Sensor producing the number of hosts in the cluster",
            DockerInfrastructure.DOCKER_HOST_COUNT);

    ConfigKey<AttributeSensor<?>> NOTIFIER = ConfigKeys.newConfigKey(new TypeToken<AttributeSensor<?>>() {},
            "enricher.availability.notifier", "The sensor on which to emit an event");

    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        subscribe(entity, getConfig(CONTAINERS), new Listener());
        subscribe(entity, getConfig(HOSTS), new Listener());
    }

    private class Listener implements SensorEventListener<Object> {
        @Override
        public void onEvent(SensorEvent<Object> event) {
            recalculate();
        }
    }

    private void recalculate() {
        int maxContainers = entity.getConfig(getConfig(MAX_CONTAINERS));
        int containers = entity.getAttribute(getConfig(CONTAINERS));
        int hosts = entity.getAttribute(getConfig(HOSTS));
        if ((maxContainers * hosts) - containers < maxContainers) {
            // Emit event with Map with properties from AutoScalerPolicy:
            Map<String, Object> properties = ImmutableMap.of(
                    AutoScalerPolicy.POOL_CURRENT_SIZE_KEY, null,
                    AutoScalerPolicy.POOL_HIGH_THRESHOLD_KEY, null,
                    AutoScalerPolicy.POOL_LOW_THRESHOLD_KEY, null,
                    AutoScalerPolicy.POOL_CURRENT_WORKRATE_KEY, null);
            emit(getConfig(NOTIFIER), properties);

        }
    }
}
