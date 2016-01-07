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
import java.util.Iterator;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jayway.jsonpath.JsonPath;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.net.HttpHeaders;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.http.HttpTool;
import org.apache.brooklyn.util.http.HttpTool.HttpClientBuilder;
import org.apache.brooklyn.util.http.HttpToolResponse;
import org.apache.brooklyn.util.core.text.TemplateProcessor;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.net.Urls;

import brooklyn.entity.mesos.framework.MesosFramework;
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

    public static final Optional<String> httpPost(Entity framework, String subUrl, String dataUrl, Map<String, Object> substitutions) {
        String targetUrl = Urls.mergePaths(framework.sensors().get(MesosFramework.FRAMEWORK_URL), subUrl); 
        String templateContents = ResourceUtils.create().getResourceAsString(dataUrl);
        String processedJson = TemplateProcessor.processTemplateContents(templateContents, substitutions);
        LOG.debug("Posting JSON to {}: {}", targetUrl, processedJson);
        URI postUri = URI.create(targetUrl);
        HttpToolResponse response = HttpTool.httpPost(
                MesosUtils.buildClient(framework),
                postUri,
                MutableMap.of(
                        HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType(),
                        HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType()),
                processedJson.getBytes());
        LOG.debug("Response: " + response.getContentAsString());
        if (!HttpTool.isStatusCodeHealthy(response.getResponseCode())) {
            LOG.warn("Invalid response code {}: {}", response.getResponseCode(), response.getReasonPhrase());
            return Optional.absent();
        } else {
            LOG.debug("Successfull call to {}: {}", targetUrl);
            return Optional.of(response.getContentAsString());
        }
    }

    public static final Optional<String> httpDelete(Entity framework, String subUrl) {
        String targetUrl = Urls.mergePaths(framework.sensors().get(MesosFramework.FRAMEWORK_URL), subUrl); 
        LOG.debug("Deleting {}", targetUrl);
        URI deleteUri = URI.create(targetUrl);
        HttpToolResponse response = HttpTool.httpDelete(
                buildClient(framework),
                deleteUri,
                MutableMap.of(
                        HttpHeaders.ACCEPT, "application/json"));
        LOG.debug("Response: " + response.getContentAsString());
        if (!HttpTool.isStatusCodeHealthy(response.getResponseCode())) {
            LOG.warn("Invalid response code {}: {}", response.getResponseCode(), response.getReasonPhrase());
            return Optional.absent();
        } else {
            LOG.debug("Successfull call to {}: {}", targetUrl);
            return Optional.of(response.getContentAsString());
        }
    }

    public static Function<JsonElement, Maybe<JsonElement>> selectM(final Predicate<JsonElement> predicate) {
        return new SelectMaybe(predicate);
    }

    protected static class SelectMaybe implements Function<JsonElement, Maybe<JsonElement>> {
        private final Predicate<JsonElement> predicate;

        public SelectMaybe(Predicate<JsonElement> predicate) {
            this.predicate = predicate;
        }

        @Override public Maybe<JsonElement> apply(JsonElement input) {
            JsonArray array = input.getAsJsonArray();
            Iterator<JsonElement> filtered = Iterators.filter(array.iterator(), predicate);
            return Maybe.next(filtered);
        }
    }


    /**
     * returns an element from a single json primitive value given a full path {@link com.jayway.jsonpath.JsonPath}
     */
    public static <T> Function<Maybe<JsonElement>,T> getPathM(final String path) {
        return new Function<Maybe<JsonElement>, T>() {
            @SuppressWarnings("unchecked")
            @Override public T apply(Maybe<JsonElement> input) {
                if (input.isAbsentOrNull()) {
                    return (T) null;
                }
                String jsonString = input.get().toString();
                Object rawElement = JsonPath.read(jsonString, path);
                return (T) rawElement;
            }
        };
    }

    public static HttpClient buildClient(Entity framework) {
        String url = framework.sensors().get(MesosFramework.FRAMEWORK_URL);
        String username = framework.config().get(MesosCluster.MESOS_USERNAME);
        String password = framework.config().get(MesosCluster.MESOS_PASSWORD);
        HttpClientBuilder builder = HttpTool.httpClientBuilder().uri(url);
        if ("true".equals(System.getProperty("jclouds.trust-all-certs"))) {
            builder.trustAll();
        }
        if (username != null && password != null) {
            builder.credentials(new UsernamePasswordCredentials(username, password));
        }
        return builder.build();
    }

}
