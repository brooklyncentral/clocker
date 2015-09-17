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
package brooklyn.entity.mesos.framework;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.feed.ConfigToAttributes;
import org.apache.brooklyn.entity.stock.BasicStartableImpl;
import org.apache.brooklyn.entity.stock.DelegateEntity;

/**
 * Mesos frameworks shared implemenatation.
 */
public abstract class MesosFrameworkImpl extends BasicStartableImpl implements MesosFramework {

    private static final Logger LOG = LoggerFactory.getLogger(MesosFramework.class);

    @Override
    public void init() {
        LOG.info("Connecting to framework id: {}", getId());
        super.init();

        ConfigToAttributes.apply(this, FRAMEWORK_URL);
        ConfigToAttributes.apply(this, MESOS_CLUSTER);
        sensors().set(Attributes.MAIN_URI, URI.create(config().get(FRAMEWORK_URL)));
    }

    static {
        RendererHints.register(MESOS_CLUSTER, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
    }

}
