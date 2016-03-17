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
package brooklyn.location.docker;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/**
 * Examples of valid specs:
 *   <ul>
 *     <li>docker:infrastructureId
 *     <li>docker:infrastructureId:(name=docker-infrastructure)
 *     <li>docker:infrastructureId:dockerHostId
 *     <li>docker:infrastructureId:dockerHostId:(name=dockerHost-brooklyn-1234,user=docker)
 *   </ul>
 */
public class DockerResolver implements LocationResolver {

    private static final Logger LOG = LoggerFactory.getLogger(DockerResolver.class);

    public static final String DOCKER = "docker";
    public static final Pattern PATTERN = Pattern.compile("("+DOCKER+"|"+DOCKER.toUpperCase()+")" + ":([a-zA-Z0-9]+)" +
            "(:([a-zA-Z0-9]+))?" + "(:\\((.*)\\))?$");
    public static final Set<String> ACCEPTABLE_ARGS = ImmutableSet.of("name");

    public static final String DOCKER_INFRASTRUCTURE_SPEC = "docker:%s";
    public static final String DOCKER_HOST_MACHINE_SPEC = "docker:%s:%s";

    private ManagementContext managementContext;

    @Override
    public void init(ManagementContext managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
    }

    @Override
    public String getPrefix() {
        return DOCKER;
    }

    @Override
    public boolean accepts(String spec, LocationRegistry registry) {
        return BasicLocationRegistry.isResolverPrefixForSpec(this, spec, true);
    }

    @Override
    public boolean isEnabled() {
        // TODO Should we calculate enablement on whether the required location/entity exists?
        return true;
    }

    @Override
    public LocationSpec<? extends Location> newLocationSpecFromString(String spec, Map<?, ?> locationFlags, LocationRegistry registry) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Resolving location '" + spec + "' with flags " + Joiner.on(",").withKeyValueSeparator("=").join(locationFlags));
        }

        Matcher matcher = PATTERN.matcher(spec);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; must specify something like docker:locId or docker:locId:(name=abc)");
        }

        String infrastructureLocId = matcher.group(2);
        if (Strings.isBlank(infrastructureLocId)) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; infrastructure location id must be non-empty");
        }
        String hostLocId = matcher.group(4);

        // TODO Could validate that the namePart matches the existing loc
        String argsPart = matcher.group(6);
        Map<String, String> argsMap = (argsPart != null) ? KeyValueParser.parseMap(argsPart) : Collections.<String,String>emptyMap();
        String namePart = argsMap.get("name");

        if (!ACCEPTABLE_ARGS.containsAll(argsMap.keySet())) {
            Set<String> illegalArgs = Sets.difference(argsMap.keySet(), ACCEPTABLE_ARGS);
            throw new IllegalArgumentException("Invalid location '"+spec+"'; illegal args "+illegalArgs+"; acceptable args are "+ACCEPTABLE_ARGS);
        }
        if (argsMap.containsKey("name") && Strings.isEmpty(namePart)) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; if name supplied then value must be non-empty");
        }

        Location infrastructureLoc = managementContext.getLocationManager().getLocation(infrastructureLocId);
        if (infrastructureLoc == null) {
            throw new IllegalArgumentException("Unknown Clocker infrastructure location id "+infrastructureLocId+", spec "+spec);
        } else if (!(infrastructureLoc instanceof DockerLocation)) {
            throw new IllegalArgumentException("Invalid location id for Clocker infrastructure, spec "+spec+"; instead matches "+infrastructureLoc);
        }

        if (hostLocId != null) {
            Location hostLoc = managementContext.getLocationManager().getLocation(hostLocId);
            if (hostLoc == null) {
                throw new IllegalArgumentException("Unknown Clocker host location id "+hostLocId+", spec "+spec);
            } else if (!(hostLoc instanceof DockerHostLocation)) {
                throw new IllegalArgumentException("Invalid location id for Clocker host, spec "+spec+"; instead matches "+hostLoc);
            }
            
            return LocationSpec.create(DockerHostLocation.class)
                    .configure(LocationConstructor.LOCATION, hostLoc)
                    .configure(SpecialBrooklynObjectConstructor.Config.SPECIAL_CONSTRUCTOR, LocationConstructor.class);
            
        } else {
            return LocationSpec.create(DockerLocation.class)
                    .configure(LocationConstructor.LOCATION, infrastructureLoc)
                    .configure(SpecialBrooklynObjectConstructor.Config.SPECIAL_CONSTRUCTOR, LocationConstructor.class);
        }
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
