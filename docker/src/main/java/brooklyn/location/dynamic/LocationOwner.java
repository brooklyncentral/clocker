/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.location.dynamic;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;

/**
 * An entity that owns a particular location.
 * <p>
 * The entity should be able to dynamically create an instance of the required type of location, and will manage
 * the lifecycle of the location in parallel with its own.
 *
 * @param L the location type
 * @param E the entity type
 */
@Beta
public interface LocationOwner<L extends Location & DynamicLocation<E, L>, E extends Entity & LocationOwner<L, E>> {

    @SetFromFlag("locationPrefix")
    ConfigKey<String> LOCATION_NAME_PREFIX = ConfigKeys.newStringConfigKey(
            "entity.dynamicLocation.prefix", "The name prefix for the location owned by this entity", "dynamic");

    @SetFromFlag("locationSuffix")
    ConfigKey<String> LOCATION_NAME_SUFFIX = ConfigKeys.newStringConfigKey(
            "entity.dynamicLocation.suffix", "The name suffix for the location owned by this entity");

    @SetFromFlag("locationName")
    BasicAttributeSensorAndConfigKey<String> LOCATION_NAME = new BasicAttributeSensorAndConfigKey<String>(String.class,
            "entity.dynamicLocation.name", "The name of the location owned by this entity (default is auto-generated using prefix and suffix keys)");

    ConfigKey<Map<String, Object>> LOCATION_FLAGS = ConfigKeys.newConfigKey(new TypeToken<Map<String, Object>>() { },
            "entity.dynamicLocation.flags", "Extra creation flags for the Location owned by this entity",
            ImmutableMap.<String, Object>of());

    AttributeSensor<Location> DYNAMIC_LOCATION = Sensors.newSensor(Location.class,
            "entity.dynamicLocation", "The location owned by this entity");

    AttributeSensor<String> LOCATION_SPEC = Sensors.newStringSensor(
            "entity.dynamicLocation.spec", "The specification string for the location owned by this entity");

    AttributeSensor<Boolean> DYNAMIC_LOCATION_STATUS = Sensors.newBooleanSensor(
            "entity.dynamicLocation.status", "The status of the location owned by this entity");

    AttributeSensor<LocationDefinition> LOCATION_DEFINITION = Sensors.newSensor(
        LocationDefinition.class, "entity.dynamicLocation.definition", "The location definition for the location owned by this entity");

    L getDynamicLocation();

    L createLocation(Map<String, ?> flags);

    boolean isLocationAvailable();

    void deleteLocation();

}
