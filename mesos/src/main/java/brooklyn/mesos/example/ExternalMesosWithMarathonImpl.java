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
package brooklyn.mesos.example;

import java.util.Collection;

import com.google.common.collect.ImmutableList;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.entity.AbstractApplication;

import brooklyn.entity.mesos.MesosCluster;
import brooklyn.entity.mesos.framework.marathon.MarathonFramework;

public class ExternalMesosWithMarathonImpl extends AbstractApplication implements ExternalMesosWithMarathon {

    protected MesosCluster mesosCluster = null;

    @Override
    public void initApp() {
        mesosCluster = addChild(EntitySpec.create(MesosCluster.class)
                .configure("mesosUrl", config().get(MESOS_URL))
                .configure("frameworkSpecs", ImmutableList.of(EntitySpec.create(MarathonFramework.class).configure("marathonUrl", config().get(MARATHON_URL)))));
    }

    @Override
    protected void doStart(Collection<? extends Location> locations) {
        mesosCluster.start(locations);
    }

}
