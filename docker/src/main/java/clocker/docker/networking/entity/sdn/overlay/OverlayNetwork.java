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
package clocker.docker.networking.entity.sdn.overlay;

import clocker.docker.networking.entity.sdn.DockerSdnProvider;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;

/**
 * A collection of machines running Docker.
 */
@Catalog(name = "Overlay Network", description = "Docker overlay networking for SDN", iconUrl = "classpath://docker-logo.png")
@ImplementedBy(OverlayNetworkImpl.class)
public interface OverlayNetwork extends DockerSdnProvider {

    AttributeSensorAndConfigKey<EntitySpec<?>, EntitySpec<?>> OVERLAY_AGENT_SPEC = ConfigKeys.newSensorAndConfigKeyWithDefault(SDN_AGENT_SPEC, EntitySpec.create(OverlayPlugin.class));

}
