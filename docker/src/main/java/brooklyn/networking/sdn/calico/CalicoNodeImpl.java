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
package brooklyn.networking.sdn.calico;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.container.docker.DockerContainer;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.networking.sdn.SdnAgentImpl;

/**
 * A single Weave router running in a {@link DockerContainer}.
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

}
