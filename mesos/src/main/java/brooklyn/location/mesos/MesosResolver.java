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
package brooklyn.location.mesos;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationRegistry;
import org.apache.brooklyn.api.location.LocationResolver.EnableableLocationResolver;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.location.BasicLocationRegistry;
import org.apache.brooklyn.core.location.LocationPropertiesFromBrooklynProperties;
import org.apache.brooklyn.core.location.dynamic.DynamicLocation;
import org.apache.brooklyn.core.location.internal.LocationInternal;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.text.KeyValueParser;
import org.apache.brooklyn.util.text.Strings;

import brooklyn.entity.mesos.MesosCluster;

/**
 * A {@link Location} backed by a {@link MesosCluster}.
 * <p>
 * Examples of valid specs:
 * <ul>
 *   <li>{@code mesos:mesosClusterId}
 *   <li>{@code mesos:mesosClusterId:(name=mesos-cluster)}
 * </ul>
 */
public class MesosResolver implements EnableableLocationResolver {

    private static final Logger LOG = LoggerFactory.getLogger(MesosResolver.class);

    public static final String MESOS = "mesos";
    public static final Pattern PATTERN = Pattern.compile("("+MESOS+"|"+MESOS.toUpperCase()+")" + ":([a-zA-Z0-9]+)" + "(:\\((.*)\\))?$");
    public static final Set<String> ACCEPTABLE_ARGS = ImmutableSet.of("name", "displayName");

    public static final String MESOS_CLUSTER_SPEC = "mesos:%s";

    private ManagementContext managementContext;

    @Override
    public void init(ManagementContext managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
    }

    @Override
    public String getPrefix() {
        return MESOS;
    }

    @Override
    public Location newLocationFromString(Map locationFlags, String spec, LocationRegistry registry) {
        return newLocationFromString(spec, registry, registry.getProperties(), locationFlags);
    }

    protected Location newLocationFromString(String spec, LocationRegistry registry, Map properties, Map locationFlags) {
        LOG.debug("Resolving location '" + spec + "' with flags " + Joiner.on(",").withKeyValueSeparator("=").join(locationFlags));
        String namedLocation = (String) locationFlags.get(LocationInternal.NAMED_SPEC_NAME.getName());

        Matcher matcher = PATTERN.matcher(spec);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; must specify something like mesos:entityId or mesos:entityId:(name=abc)");
        }

        String argsPart = matcher.group(4);
        Map<String, String> argsMap = (argsPart != null) ? KeyValueParser.parseMap(argsPart) : Collections.<String,String>emptyMap();
        String displayNamePart = argsMap.get("displayName");
        String namePart = argsMap.get("name");

        if (!ACCEPTABLE_ARGS.containsAll(argsMap.keySet())) {
            Set<String> illegalArgs = Sets.difference(argsMap.keySet(), ACCEPTABLE_ARGS);
            throw new IllegalArgumentException("Invalid location '"+spec+"'; illegal args "+illegalArgs+"; acceptable args are "+ACCEPTABLE_ARGS);
        }
        if (argsMap.containsKey("displayName") && Strings.isEmpty(displayNamePart)) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; if displayName supplied then value must be non-empty");
        }
        if (argsMap.containsKey("name") && Strings.isEmpty(namePart)) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; if name supplied then value must be non-empty");
        }

        Map<String, Object> filteredProperties = new LocationPropertiesFromBrooklynProperties().getLocationProperties(MESOS, namedLocation, properties);
        MutableMap<String, Object> flags = MutableMap.<String, Object>builder().putAll(filteredProperties).putAll(locationFlags).build();

        String mesosClusterId = matcher.group(2);
        if (Strings.isBlank(mesosClusterId)) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; mesos cluster entity id must be non-empty");
        }

        // Build the display name
        StringBuilder name = new StringBuilder();
        if (displayNamePart != null) {
            name.append(displayNamePart);
        } else {
            name.append("Mesos Cluster ").append(mesosClusterId);
        }
        final String displayName =  name.toString();

        // Build the location name
        name = new StringBuilder();
        if (namePart != null) {
            name.append(namePart);
        } else {
            name.append("mesos-");
            name.append(mesosClusterId);
        }
        final String locationName =  name.toString();
        MesosCluster cluster = (MesosCluster) managementContext.getEntityManager().getEntity(mesosClusterId);
        Iterable<Location> managedLocations = managementContext.getLocationManager().getLocations();

        for (Location location : managedLocations) {
            if (location instanceof MesosLocation) {
                if (((MesosLocation) location).getOwner().getId().equals(mesosClusterId)) {
                    return location;
                }
            }
        }
        LocationSpec<MesosLocation> locationSpec = LocationSpec.create(MesosLocation.class)
                .configure(flags)
                .configure(DynamicLocation.OWNER, cluster)
                .configure(LocationInternal.NAMED_SPEC_NAME, locationName)
                .displayName(displayName);
        return managementContext.getLocationManager().createLocation(locationSpec);
    }

    @Override
    public boolean accepts(String spec, LocationRegistry registry) {
        return BasicLocationRegistry.isResolverPrefixForSpec(this, spec, true);
    }

    @Override
    public boolean isEnabled() {
        return true;
//        return Iterables.tryFind(managementContext.getEntityManager().getEntities(), Predicates.instanceOf(MesosCluster.class)).isPresent();
    }

}
