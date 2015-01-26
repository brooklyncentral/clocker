/*
 * Copyright 2014 by Cloudsoft Corporation Limited
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
package brooklyn.entity.container.sdn.weave;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.container.sdn.SdnAgent;
import brooklyn.entity.container.sdn.SdnProvider;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.net.Cidr;

/**
 * A collection of machines running Weave.
 */
@Catalog(name = "Weave Infrastructure", description = "Weave SDN.")
@ImplementedBy(WeaveNetworkImpl.class)
public interface WeaveNetwork extends SdnProvider {

    @SetFromFlag("version")
    ConfigKey<String> WEAVE_VERSION = ConfigKeys.newStringConfigKey("weave.version", "The Weave SDN version number");

    @SetFromFlag("cidr")
    ConfigKey<Cidr> CIDR = SdnAgent.CIDR;

    @SetFromFlag("weavePort")
    ConfigKey<Integer> WEAVE_PORT = WeaveContainer.WEAVE_PORT;

}
