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
package brooklyn.entity.mesos;

import java.net.URI;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.net.HttpHeaders;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.http.HttpTool;
import org.apache.brooklyn.util.core.http.HttpToolResponse;
import org.apache.brooklyn.util.core.text.TemplateProcessor;

import brooklyn.location.mesos.MesosLocation;

public class MesosUtils {

    private static final Logger LOG = LoggerFactory.getLogger(MesosUtils.class);

    /** Do not instantiate. */
    private MesosUtils() { }

    public static final Predicate<Entity> sameCluster(Entity entity) {
        Preconditions.checkNotNull(entity, "entity");
        return new SameClusterPredicate(entity.getId());
    }

    public static class SameClusterPredicate implements Predicate<Entity> {

        private final String id;

        public SameClusterPredicate(String id) {
            this.id = Preconditions.checkNotNull(id, "id");
        }

        @Override
        public boolean apply(@Nullable Entity input) {
            // Check if entity is deployed to a MesosLocation
            Optional<Location> lookup = Iterables.tryFind(input.getLocations(), Predicates.instanceOf(MesosLocation.class));
            if (lookup.isPresent()) {
                MesosLocation location = (MesosLocation) lookup.get();
                return id.equals(location.getOwner().getId());
            } else {
                return false;
            }
        }
    };

    public static final Optional<String> postJson(String targetUrl, String dataUrl, Map<String, Object> substitutions) {
        String templateContents = ResourceUtils.create().getResourceAsString(dataUrl);
        String processedJson = TemplateProcessor.processTemplateContents(templateContents, substitutions);
        LOG.debug("Posting JSON to {}: {}", targetUrl, processedJson);
        URI postUri = URI.create(targetUrl);
        HttpToolResponse response = HttpTool.httpPost(
                HttpTool.httpClientBuilder().uri(postUri).build(),
                postUri,
                MutableMap.of(
                        HttpHeaders.CONTENT_TYPE, "application/json",
                        HttpHeaders.ACCEPT, "application/json"),
                processedJson.getBytes());
        if (!HttpTool.isStatusCodeHealthy(response.getResponseCode())) {
            LOG.warn("Invalid response code: "+response);
            return Optional.absent();
        } else {
            LOG.debug("Success full call to {}: {}", targetUrl, response.getContentAsString());
            return Optional.of(response.getContentAsString());
        }
    }

}
