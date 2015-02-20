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
import java.util.Set;

import javax.annotation.Nullable;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.container.docker.DockerContainer;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.reflect.TypeToken;

/**
 * SDN attributes and configuration keys.
 */
public class SdnAttributes {

    public static final ConfigKey<Collection<String>> NETWORK_LIST = ConfigKeys.newConfigKey(
            new TypeToken<Collection<String>>() { }, "network.list", "Collection of extra networks to create for an entity", Collections.<String>emptyList());

    public static final ConfigKey<Boolean> SDN_ENABLE = ConfigKeys.newBooleanConfigKey("sdn.enable", "Enable Sofware-Defined Networking", Boolean.FALSE);
    public static final ConfigKey<Boolean> SDN_DEBUG = ConfigKeys.newBooleanConfigKey("sdn.debug", "Enable SDN debugging utility installation", Boolean.FALSE);

    public static final AttributeSensor<Set<String>> ATTACHED_NETWORKS = Sensors.newSensor(new TypeToken<Set<String>>() { },
            "sdn.networks.attached", "The set of networks that an entity is attached to");

    public static final Predicate<Entity> containerAttached(String networkId) {
        Preconditions.checkNotNull(networkId, "networkId");
        return new ContainerAttachedPredicate(networkId);
    }

    public static class ContainerAttachedPredicate implements Predicate<Entity> {

        private final String id;

        public ContainerAttachedPredicate(String id) {
            this.id = Preconditions.checkNotNull(id, "id");
        }

        @Override
        public boolean apply(@Nullable Entity input) {
            if (input instanceof DockerContainer) {
                Set<String> networks = input.getAttribute(SdnAttributes.ATTACHED_NETWORKS);
                if (networks != null) return networks.contains(id);
            }
            return false;
        }
    };

}
