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
package clocker.mesos.location.framework;

import java.io.Closeable;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clocker.mesos.entity.framework.MesosFramework;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.Maps;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.core.location.AbstractLocation;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

public abstract class MesosFrameworkLocation extends AbstractLocation implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(MesosFrameworkLocation.class);

    @SetFromFlag("owner")
    protected MesosFramework framework;

    public MesosFrameworkLocation() {
        this(Maps.newLinkedHashMap());
    }

    public MesosFrameworkLocation(Map properties) {
        super(properties);

        if (isLegacyConstruction()) {
            init();
        }
    }

    public boolean isSupported(Entity entity) {
        List<Class<? extends Entity>> supported = framework.getSupported();
        for (Class<? extends Entity> type : supported) {
            LOG.info("Entity {} type: {}", entity, entity.getEntityType().getName());
            if (type.isAssignableFrom(entity.getClass())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ToStringHelper string() {
        return super.string()
                .add("owner", framework);
    }

}
