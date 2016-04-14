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
package clocker.docker.location.strategy.basic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clocker.docker.location.DockerHostLocation;
import clocker.docker.location.strategy.BasicDockerPlacementStrategy;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

/**
 * Strategy that requires the hostname of the Docker location to match a particular regexp.
 */
public class HostnamePlacementStrategy extends BasicDockerPlacementStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(HostnamePlacementStrategy.class);

    @SetFromFlag("hostname")
    public static final ConfigKey<String> HOSTNAME_PATTERN = ConfigKeys.newStringConfigKey(
            "docker.constraint.hostname",
            "The pattern the required hostname must match");

    @Override
    public boolean apply(DockerHostLocation input) {
        String constraint = Preconditions.checkNotNull(config().get(HOSTNAME_PATTERN), "pattern");
        String hostname = Strings.nullToEmpty(input.getMachine().getHostname());
        boolean match = hostname.matches(constraint);
        LOG.debug("Hostname {} {} {}",
                new Object[] { hostname, match ? "matches" : "not", constraint });

        return match;
    }
}
