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
package brooklyn.networking.sdn.weave;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import brooklyn.networking.sdn.DockerSdnProvider;

/**
 * A collection of machines running Weave.
 */
@Catalog(name = "Weave Infrastructure", description = "Weave SDN", iconUrl = "classpath://weaveworks-logo.png")
@ImplementedBy(WeaveNetworkImpl.class)
public interface WeaveNetwork extends DockerSdnProvider {

    @SetFromFlag("version")
    ConfigKey<String> WEAVE_VERSION = ConfigKeys.newStringConfigKey("weave.version", "The Weave SDN version number", "1.1.2");

    @SetFromFlag("weavePort")
    ConfigKey<Integer> WEAVE_PORT = WeaveContainer.WEAVE_PORT;

    ConfigKey<EntitySpec<?>> WEAVE_ROUTER_SPEC = ConfigKeys.newConfigKeyWithDefault(SDN_AGENT_SPEC.getConfigKey(), EntitySpec.create(WeaveContainer.class));

}
