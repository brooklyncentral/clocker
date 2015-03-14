/*
 * Copyright 2013-2014 by Cloudsoft Corporation Limited
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

import org.jclouds.cloudstack.filters.QuerySigner;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.http.HttpRequest;
import org.jclouds.http.HttpResponse;
import org.jclouds.softlayer.SoftLayerApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.networking.cloudstack.HttpUtil;
import brooklyn.util.guava.Maybe;
import brooklyn.util.http.HttpToolResponse;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

public class SoftLayerRestClient {

    private static final Logger LOG = LoggerFactory.getLogger(SoftLayerRestClient.class);

    private final String endpoint;
    private final String apiKey;
    //context knows it and gives us the signer; included for completeness only
    @SuppressWarnings("unused")
    private final String secretKey;
    private final ComputeServiceContext context;

    public static SoftLayerRestClient newInstance(JcloudsLocation loc) {
        return new SoftLayerRestClient(loc.getConfig(JcloudsLocation.CLOUD_ENDPOINT), loc.getIdentity(), loc.getCredential(), loc.getComputeService().getContext());
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

    protected JsonArray listVpcsJson() {
        Multimap<String, String> params = ArrayListMultimap.create();
        params.put("command", "listVPCs");

        params.put("apiKey", this.apiKey);
        params.put("response", "json");

        HttpRequest request = HttpRequest.builder()
                .method("GET")
                .endpoint(this.endpoint)
                .addQueryParams(params)
                .addHeader("Accept", "application/json")
                .build();

        request = getQuerySigner().filter(request);

        HttpToolResponse response = HttpUtil.invoke(request);

        JsonElement jr = json(response);
        LOG.debug(pretty(jr));

        JsonElement vpcs = jr.getAsJsonObject().get("listvpcsresponse").getAsJsonObject().get("vpc");
        return vpcs == null ? null : vpcs.getAsJsonArray();
    }

    public String createVpc(String cidr, String displayText, String name, String vpcOfferingId, String zoneId) {
        Multimap<String, String> params = ArrayListMultimap.create();
        params.put("command", "createVPC");
        params.put("cidr", cidr);
        params.put("displayText", displayText);
        params.put("name", name);
        params.put("vpcOfferingId", vpcOfferingId);
        params.put("zoneId", zoneId);

        params.put("apiKey", this.apiKey);
        params.put("response", "json");

        HttpRequest request = HttpRequest.builder()
                .method("GET")
                .endpoint(this.endpoint)
                .addQueryParams(params)
                .addHeader("Accept", "application/json")
                .build();

        request = getQuerySigner().filter(request);

        HttpToolResponse response = HttpUtil.invoke(request);
        // todo: handle non-2xx response
        return "";
    }

    public String deleteVpc(String vpcId) {
        Multimap<String, String> params = ArrayListMultimap.create();
        params.put("command", "deleteVPC");
        params.put("id", vpcId);

        params.put("apiKey", this.apiKey);
        params.put("response", "json");

        HttpRequest request = HttpRequest.builder()
                .method("GET")
                .endpoint(this.endpoint)
                .addQueryParams(params)
                .addHeader("Accept", "application/json")
                .build();

        request = getQuerySigner().filter(request);

        HttpToolResponse response = HttpUtil.invoke(request);
        // todo: handle non-2xx response
        return "";
    }

    public String createVpcTier(String name, String displayText,
                                String networkOfferingId,
                                String zoneId, String vpcId,
                                String gateway, String netmask) {

        //vpcid
        Multimap<String, String> params = ArrayListMultimap.create();
        params.put("command", "createNetwork");

        params.put("displayText", displayText);
        params.put("name", name);
        params.put("networkofferingid", networkOfferingId);
        params.put("zoneid", zoneId);
        params.put("vpcid", vpcId);
        params.put("gateway", gateway);
        params.put("netmask", netmask);

        params.put("apiKey", this.apiKey);
        params.put("response", "json");

        LOG.debug("createVpcTier GET " + params);

        HttpRequest request = HttpRequest.builder()
                .method("GET")
                .endpoint(this.endpoint)
                .addQueryParams(params)
                .addHeader("Accept", "application/json")
                .build();

        request = getQuerySigner().filter(request);

        HttpToolResponse response = HttpUtil.invoke(request);
        // TODO does non-2xx response need to be handled separately ?

        JsonElement jr = json(response);
        LOG.debug("createVpcTier GOT " + jr);

        // seems this is created immediately
        return jr.getAsJsonObject().get("createnetworkresponse")
                .getAsJsonObject().get("network")
                .getAsJsonObject().get("id")
                .getAsString();
    }

    private HttpToolResponse disableEgressFirewallForProtocol(String networkId, String protocol) {
        Multimap<String, String> params = ArrayListMultimap.create();
        params.put("command", "createEgressFirewallRule");

        params.put("networkid", networkId);
        params.put("protocol", protocol);
        params.put("cidrlist", "0.0.0.0/0");
        if (protocol.equals("TCP") || protocol.equals("UDP")) {
            params.put("startport", "1");
            params.put("endport", "65535");
        } else if (protocol.equals("ICMP")) {
            params.put("icmpcode", "-1");
            params.put("icmptype", "-1");
        } else {
            throw new IllegalArgumentException("Protocol " + protocol + " is not known");
        }

        params.put("apiKey", this.apiKey);
        params.put("response", "json");

        LOG.debug("createEgressFirewallRule GET " + params);

        HttpRequest request = HttpRequest.builder()
                .method("GET")
                .endpoint(this.endpoint)
                .addQueryParams(params)
                .addHeader("Accept", "application/json")
                .build();

        request = getQuerySigner().filter(request);

        request.getEndpoint().toString().replace("+", "%2B");
        //request = request.toBuilder().endpoint(uriBuilder(request.getEndpoint()).query(decodedParams).build()).build();

        HttpToolResponse response = HttpUtil.invoke(request);
        // TODO does non-2xx response need to be handled separately ?
        return response;
    }

    public Maybe<String> findVpcIdFromNetworkId(final String networkId) {
        Multimap<String, String> params = ArrayListMultimap.create();
        params.put("command", "listNetworks");
        params.put("apiKey", this.apiKey);
        params.put("response", "json");

        HttpRequest request = HttpRequest.builder()
                .method("GET")
                .endpoint(this.endpoint)
                .addQueryParams(params)
                .addHeader("Accept", "application/json")
                .build();

        request = getQuerySigner().filter(request);

        HttpToolResponse response = HttpUtil.invoke(request);
        JsonElement networks = json(response);
        LOG.debug("LIST NETWORKS\n" + pretty(networks));
        //get the first network object
        Optional<JsonElement> matchingNetwork = Iterables.tryFind(networks.getAsJsonObject().get("listnetworksresponse")
                .getAsJsonObject().get("network").getAsJsonArray(), new Predicate<JsonElement>() {
            @Override
            public boolean apply(JsonElement jsonElement) {
                JsonObject matchingNetwork = jsonElement.getAsJsonObject();
                return matchingNetwork.get("id").getAsString().equals(networkId);
            }
        });
        return Maybe.of(matchingNetwork.get().getAsJsonObject().get("vpcid").getAsString());
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