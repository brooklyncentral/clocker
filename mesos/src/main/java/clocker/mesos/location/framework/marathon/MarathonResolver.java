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
package clocker.mesos.location.framework.marathon;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Joiner;

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
import org.apache.brooklyn.util.text.Strings;

/**
 * Examples of valid specs:
 *   <ul>
 *     <li>marathon:frameworkId
 */
public class MarathonResolver implements LocationResolver {

    private static final Logger LOG = LoggerFactory.getLogger(MarathonResolver.class);

    public static final String MARATHON = "marathon";
    public static final Pattern PATTERN = Pattern.compile("("+MARATHON+"|"+MARATHON.toUpperCase()+")" + ":([a-zA-Z0-9]+)");
    public static final String MARATHON_FRAMEWORK_SPEC = MARATHON + ":%s";

    private ManagementContext managementContext;

    @Override
    public void init(ManagementContext managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
    }

    @Override
    public String getPrefix() {
        return MARATHON;
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
            throw new IllegalArgumentException("Invalid location '"+spec+"'; must specify something like marathon:locationId");
        }

        String marathonLocId = matcher.group(2);
        if (Strings.isBlank(marathonLocId)) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; Marathon framework location id must be non-empty");
        }

        Location marathonLoc = managementContext.getLocationManager().getLocation(marathonLocId);
        if (marathonLoc == null) {
            throw new IllegalArgumentException("Unknown Marathon Framework location id "+marathonLocId+", spec "+spec);
        } else if (!(marathonLoc instanceof MarathonLocation)) {
            throw new IllegalArgumentException("Invalid location id for Marathon Framework, spec "+spec+"; instead matches "+marathonLoc);
        }

        return LocationSpec.create(MarathonLocation.class)
                .configure(LocationConstructor.LOCATION, marathonLoc)
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
