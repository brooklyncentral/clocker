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
package brooklyn.location.mesos.framework;

import java.io.Closeable;
import java.util.List;
import java.util.Map;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.Maps;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.core.location.AbstractLocation;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import brooklyn.entity.mesos.framework.MesosFramework;

public abstract class MesosFrameworkLocation extends AbstractLocation implements Closeable {

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
            if (type.isAssignableFrom(entity.getClass())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ToStringHelper string() {
        return super.string();
    }

}
