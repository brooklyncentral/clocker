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
package brooklyn.networking.sdn;

import java.util.Collection;
import java.util.Collections;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;

import com.google.common.reflect.TypeToken;

/**
 * SDN attributes and configuration keys.
 */
public class SdnAttributes {

    public static final ConfigKey<Collection<String>> NETWORK_LIST = ConfigKeys.newConfigKey(
            new TypeToken<Collection<String>>() { }, "network.list", "Collection of extra networks to create for an entity", Collections.<String>emptyList());

    public static final ConfigKey<Boolean> SDN_ENABLE = ConfigKeys.newBooleanConfigKey("sdn.enable", "Enable Sofware-Defined Networking", Boolean.FALSE);
    public static final ConfigKey<Boolean> SDN_DEBUG = ConfigKeys.newBooleanConfigKey("sdn.debug", "Enable SDN debugging utility installation", Boolean.FALSE);

}
