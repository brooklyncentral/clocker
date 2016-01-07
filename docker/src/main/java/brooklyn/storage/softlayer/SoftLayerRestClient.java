/*
 * Copyright 2013-2015 by Cloudsoft Corporation Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.storage.softlayer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import org.jclouds.cloudstack.filters.QuerySigner;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.http.HttpResponse;
import org.jclouds.softlayer.SoftLayerApi;

import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.util.http.HttpToolResponse;

public class SoftLayerRestClient {

    private static final Logger LOG = LoggerFactory.getLogger(SoftLayerRestClient.class);

    private final String endpoint;
    private final String apiKey;
    @SuppressWarnings("unused")
    private final String secretKey;
    private final ComputeServiceContext context;

    public static SoftLayerRestClient newInstance(JcloudsLocation loc) {
        return new SoftLayerRestClient(loc.config().get(JcloudsLocation.CLOUD_ENDPOINT), loc.getIdentity(), loc.getCredential(), loc.getComputeService().getContext());
    }

    public SoftLayerRestClient(String endpoint, String apiKey, String secretKey, ComputeServiceContext context) {
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.endpoint = endpoint;
        this.context = context;
    }

    public void close() {
        context.close();
    }

    public SoftLayerApi getSoftLayerApi() {
        return context.unwrapApi(SoftLayerApi.class);
    }

    public QuerySigner getQuerySigner() {
        return context.utils().injector().getInstance(QuerySigner.class);
    }

    /* JSON Helpers */

    public static JsonElement json(HttpToolResponse response) {
        return json(new ByteArrayInputStream(response.getContent()));
    }

    public static JsonElement json(HttpResponse response) throws IOException {
        return json(response.getPayload().openStream());
    }

    public static JsonElement json(InputStream is) {
        JsonParser parser = new JsonParser();
        JsonReader reader = null;
        try {
            reader = new JsonReader(new InputStreamReader(is, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        JsonElement el = parser.parse(reader);
        return el;
    }

    public static String pretty(InputStream is) {
        return pretty(json(is));
    }

    public static String pretty(JsonElement js) {
        return gson().toJson(js);
    }

    protected static Gson gson() {
        return new GsonBuilder()
                .setPrettyPrinting()
                .create();
    }
}