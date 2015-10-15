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
package brooklyn.location.mesos;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.api.location.NoMachinesAvailableException;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.location.AbstractLocation;
import org.apache.brooklyn.core.location.LocationConfigKeys;
import org.apache.brooklyn.core.location.dynamic.DynamicLocation;
import org.apache.brooklyn.core.location.dynamic.LocationOwner;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import brooklyn.entity.mesos.MesosAttributes;
import brooklyn.entity.mesos.MesosCluster;
import brooklyn.location.mesos.framework.MesosFrameworkLocation;

public class MesosLocation extends AbstractLocation implements MachineProvisioningLocation<MachineLocation>, DynamicLocation<MesosCluster, MesosLocation>, Closeable {

    public static final ConfigKey<Entity> MESOS_CLUSTER = MesosAttributes.MESOS_CLUSTER.getConfigKey();
    public static final String PREFIX = "mesos-";

    private static final Logger LOG = LoggerFactory.getLogger(MesosLocation.class);

    @SetFromFlag("owner")
    private MesosCluster cluster;

    public MesosLocation() {
        this(Maps.newLinkedHashMap());
    }

    public MesosLocation(Map properties) {
        super(properties);

        if (isLegacyConstruction()) {
            init();
        }
    }

    public MesosCluster getMesosCluster() { return cluster; }

    @Override
    public MesosCluster getOwner() {
        return cluster;
    }

    @Override
    public void close() throws IOException {
        LOG.info("Close called on Mesos cluster: {}", this);
    }

    @Override
    public ToStringHelper string() {
        return super.string()
                .omitNullValues()
                .add("cluster", cluster);
    }

    @Override
    public MachineLocation obtain(Map<?, ?> flags) throws NoMachinesAvailableException {
        // Check context for entity being deployed
        Object context = flags.get(LocationConfigKeys.CALLER_CONTEXT.getName());
        if (context != null && !(context instanceof Entity)) {
            throw new IllegalStateException("Invalid location context: " + context);
        }
        Entity entity = (Entity) context;

        // Get the available hosts based on placement strategies
        Collection<Entity> frameworks = cluster.sensors().get(MesosCluster.MESOS_FRAMEWORKS).getMembers();
        Iterable<MesosFrameworkLocation> locations = Iterables.transform(Iterables.filter(frameworks, Predicates.instanceOf(LocationOwner.class)),
                new Function<Entity, MesosFrameworkLocation>() {
                    @Override
                    public MesosFrameworkLocation apply(Entity input) {
                        return (MesosFrameworkLocation) ((LocationOwner) input).getDynamicLocation();
                    }});
        for (MesosFrameworkLocation framework : locations) {
            if (framework.isSupported(entity) && framework instanceof MachineProvisioningLocation) {
                return ((MachineProvisioningLocation) framework).obtain(flags);
            }
        }

        // No suitable framework found
        throw new NoMachinesAvailableException("No framework found to start entity: " + entity);
    }

    @Override
    public MachineProvisioningLocation<MachineLocation> newSubLocation(Map<?, ?> newFlags) {
        return null;
    }

    @Override
    public void release(MachineLocation machine) {
        
    }

    @Override
    public Map<String, Object> getProvisioningFlags(Collection<String> tags) {
        return null;
    }

}
