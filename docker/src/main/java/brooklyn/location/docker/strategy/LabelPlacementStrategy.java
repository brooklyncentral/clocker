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
package brooklyn.location.docker.strategy;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;

import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import brooklyn.location.docker.DockerHostLocation;

/**
 * Placement strategy that checks labels on hosts.
 */
public class LabelPlacementStrategy extends BasicDockerPlacementStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(LabelPlacementStrategy.class);

    @SetFromFlag("labels")
    public static final ConfigKey<List<String>> LABELS = ConfigKeys.newConfigKey(
            new TypeToken<List<String>>() { },
            "docker.constraint.labels",
            "The list of required labels", ImmutableList.<String>of());

    @Override
    public boolean apply(DockerHostLocation input) {
        Set<String> labels = MutableSet.copyOf(config().get(LABELS));
        if (labels.isEmpty()) return true;
        Set<String> tags = MutableSet.copyOf(Iterables.transform(input.getMachine().tags().getTags(), Functions.toStringFunction()));
        labels.removeAll(tags);
        LOG.debug("Host {} : Tags {} : Remaining {}",
                new Object[] { input, Iterables.toString(tags), labels.isEmpty() ? "none" : Iterables.toString(labels) });

        return labels.isEmpty();
    }
}
