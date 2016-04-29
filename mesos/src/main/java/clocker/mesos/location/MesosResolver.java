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
package clocker.mesos.location;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clocker.mesos.entity.MesosCluster;

import com.google.common.annotations.Beta;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationRegistry;
import org.apache.brooklyn.api.location.LocationResolver;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.location.BasicLocationRegistry;
import org.apache.brooklyn.core.objs.proxy.SpecialBrooklynObjectConstructor;
import org.apache.brooklyn.util.text.KeyValueParser;
import org.apache.brooklyn.util.text.Strings;

/**
 * A {@link Location} backed by a {@link MesosCluster}.
 * <p>
 * Examples of valid specs:
 * <ul>
 *   <li>{@code mesos:mesosClusterId}
 *   <li>{@code mesos:mesosClusterId:(name=mesos-cluster)}
 * </ul>
 */
public class MesosResolver implements LocationResolver {

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
    public boolean accepts(String spec, LocationRegistry registry) {
        return BasicLocationRegistry.isResolverPrefixForSpec(this, spec, true);
    }

    @Override
    public boolean isEnabled() {
        // TODO Could lookup if location exists - should we?
        return true;
    }

    @Override
    public LocationSpec<? extends Location> newLocationSpecFromString(String spec, Map<?, ?> locationFlags, LocationRegistry registry) {
        LOG.debug("Resolving location '" + spec + "' with flags " + Joiner.on(",").withKeyValueSeparator("=").join(locationFlags));

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

        String mesosLocId = matcher.group(2);
        if (Strings.isBlank(mesosLocId)) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; mesos cluster location id must be non-empty");
        }

        Location mesosLoc = managementContext.getLocationManager().getLocation(mesosLocId);
        if (mesosLoc == null) {
            throw new IllegalArgumentException("Unknown Mesos location id "+mesosLocId+", spec "+spec);
        } else if (!(mesosLoc instanceof MesosLocation)) {
            throw new IllegalArgumentException("Invalid location id for Mesos, spec "+spec+"; instead matches "+mesosLoc);
        }

        return LocationSpec.create(MesosLocation.class)
                .configure(LocationConstructor.LOCATION, mesosLoc)
                .configure(SpecialBrooklynObjectConstructor.Config.SPECIAL_CONSTRUCTOR, LocationConstructor.class);
    }
    
    @Beta
    public static class LocationConstructor implements SpecialBrooklynObjectConstructor {
        public static ConfigKey<Location> LOCATION = ConfigKeys.newConfigKey(Location.class, "resolver.location");
        
        @SuppressWarnings("unchecked")
        @Override
        public <T> T create(ManagementContext mgmt, Class<T> type, AbstractBrooklynObjectSpec<?, ?> spec) {
            return (T) checkNotNull(spec.getConfig().get(LOCATION), LOCATION.getName());
        }
    }
}
