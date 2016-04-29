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
package clocker.docker.networking.entity.sdn.calico;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clocker.docker.entity.container.DockerContainer;
import clocker.docker.networking.entity.sdn.SdnAgentImpl;

import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.core.feed.ConfigToAttributes;
import org.apache.brooklyn.entity.stock.DelegateEntity;

/**
 * Calico node services running in {@link DockerContainer containers}.
 */
public class CalicoNodeImpl extends SdnAgentImpl implements CalicoNode {

    private static final Logger LOG = LoggerFactory.getLogger(CalicoNode.class);

    public void init() {
        super.init();

        ConfigToAttributes.apply(this, ETCD_NODE);
    }

    @Override
    public Class getDriverInterface() {
        return CalicoNodeDriver.class;
    }

    static {
        RendererHints.register(ETCD_NODE, RendererHints.openWithUrl(DelegateEntity.EntityUrl.entityUrl()));
    }

}
