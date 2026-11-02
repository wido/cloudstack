//Licensed to the Apache Software Foundation (ASF) under one
//or more contributor license agreements.  See the NOTICE file
//distributed with this work for additional information
//regarding copyright ownership.  The ASF licenses this file
//to you under the Apache License, Version 2.0 (the
//"License"); you may not use this file except in compliance
//the License.  You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing,
//software distributed under the License is distributed on an
//"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
//KIND, either express or implied.  See the License for the
//specific language governing permissions and limitations
//under the License.
package org.apache.cloudstack.network.routedsubnet.http;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.network.routedsubnet.Announcement;
import org.apache.cloudstack.network.routedsubnet.RouteAnnouncementException;
import org.apache.cloudstack.network.routedsubnet.RouteAnnouncer;
import org.apache.commons.lang3.StringUtils;

import com.cloud.utils.component.AdapterBase;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Announces routes to any speaker that implements the contract documented in the plugin's
 * README: a bearer-token protected HTTP API with
 * <pre>
 *   GET    /v1/routes                 -> [{"prefix": "...", "next_hop": "..."}, ...]
 *   PUT    /v1/routes/{prefix}        body {"next_hop": "..."}, idempotent
 *   DELETE /v1/routes/{prefix}?next_hop=...  idempotent, 404 counts as done
 *   GET    /v1/health                 -> {"status": "ok", "instance": "<id picked at start>"}
 * </pre>
 * The plugin sends prefix and next hop and nothing else; ASNs, peers, communities and filters
 * are the speaker's own configuration. The shipped exabgp-announcer.py is one implementation.
 */
public class HttpRouteAnnouncer extends AdapterBase implements RouteAnnouncer, Configurable {

    public static final String NAME = "http";

    public static final ConfigKey<String> Endpoints = new ConfigKey<>(ConfigKey.CATEGORY_NETWORK, String.class,
            "routed.subnet.announcer.http.endpoints", "",
            "Comma separated base URLs of the route announcer daemons of the zone, for example http://10.0.0.5:8900,http://10.0.0.6:8900. Every announcement goes to every endpoint or to the first that accepts, as routed.subnet.announcer.strategy says.",
            true, ConfigKey.Scope.Zone);

    public static final ConfigKey<String> Token = new ConfigKey<>(ConfigKey.CATEGORY_NETWORK, String.class,
            "routed.subnet.announcer.http.token", "",
            "Bearer token the route announcer daemons expect.",
            true, ConfigKey.Scope.Zone);

    public static final ConfigKey<Integer> Timeout = new ConfigKey<>(ConfigKey.CATEGORY_NETWORK, Integer.class,
            "routed.subnet.announcer.http.timeout", "10",
            "Seconds to wait for a route announcer daemon before treating it as unreachable.",
            true, ConfigKey.Scope.Zone);

    private HttpClient client;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getConfigComponentName() {
        return HttpRouteAnnouncer.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] {Endpoints, Token, Timeout};
    }

    @Override
    public List<String> getEndpoints(long zoneId) {
        List<String> endpoints = new ArrayList<>();
        String configured = Endpoints.valueIn(zoneId);
        if (StringUtils.isBlank(configured)) {
            return endpoints;
        }
        for (String endpoint : configured.split(",")) {
            String trimmed = endpoint.trim();
            while (trimmed.endsWith("/")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            if (!trimmed.isEmpty()) {
                endpoints.add(trimmed);
            }
        }
        return endpoints;
    }

    @Override
    public void announce(long zoneId, String endpoint, String prefix, String nextHop) throws RouteAnnouncementException {
        JsonObject body = new JsonObject();
        body.addProperty("next_hop", nextHop);
        HttpResponse<String> response = send(zoneId, request(zoneId, endpoint, "/v1/routes/" + encode(prefix))
                .PUT(HttpRequest.BodyPublishers.ofString(body.toString())).header("Content-Type", "application/json").build());
        if (response.statusCode() / 100 != 2) {
            throw new RouteAnnouncementException(String.format("%s rejected %s via %s: HTTP %d %s", endpoint, prefix, nextHop, response.statusCode(), response.body()));
        }
    }

    @Override
    public void withdraw(long zoneId, String endpoint, String prefix, String nextHop) throws RouteAnnouncementException {
        String query = nextHop == null ? "" : "?next_hop=" + encode(nextHop);
        HttpResponse<String> response = send(zoneId, request(zoneId, endpoint, "/v1/routes/" + encode(prefix) + query).DELETE().build());
        if (response.statusCode() / 100 != 2 && response.statusCode() != 404) {
            throw new RouteAnnouncementException(String.format("%s refused to withdraw %s: HTTP %d %s", endpoint, prefix, response.statusCode(), response.body()));
        }
    }

    @Override
    public Set<Announcement> list(long zoneId, String endpoint) throws RouteAnnouncementException {
        HttpResponse<String> response = send(zoneId, request(zoneId, endpoint, "/v1/routes").GET().build());
        if (response.statusCode() / 100 != 2) {
            throw new RouteAnnouncementException(String.format("%s could not list routes: HTTP %d %s", endpoint, response.statusCode(), response.body()));
        }
        Set<Announcement> routes = new HashSet<>();
        try {
            JsonElement parsed = JsonParser.parseString(response.body());
            JsonArray array = parsed.isJsonArray() ? parsed.getAsJsonArray() : parsed.getAsJsonObject().getAsJsonArray("routes");
            if (array == null) {
                throw new RouteAnnouncementException(endpoint + " returned no route list");
            }
            for (JsonElement element : array) {
                JsonObject route = element.getAsJsonObject();
                routes.add(new Announcement(route.get("prefix").getAsString(), route.get("next_hop").getAsString()));
            }
        } catch (RuntimeException e) {
            throw new RouteAnnouncementException(endpoint + " returned an unreadable route list: " + e.getMessage(), e);
        }
        return routes;
    }

    @Override
    public String getInstanceId(long zoneId, String endpoint) throws RouteAnnouncementException {
        HttpResponse<String> response = send(zoneId, request(zoneId, endpoint, "/v1/health").GET().build());
        if (response.statusCode() / 100 != 2) {
            throw new RouteAnnouncementException(String.format("%s is not healthy: HTTP %d %s", endpoint, response.statusCode(), response.body()));
        }
        try {
            JsonObject health = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonElement instance = health.get("instance");
            return instance == null ? "" : instance.getAsString();
        } catch (RuntimeException e) {
            throw new RouteAnnouncementException(endpoint + " returned an unreadable health reply: " + e.getMessage(), e);
        }
    }

    private HttpRequest.Builder request(long zoneId, String endpoint, String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint + path))
                .timeout(Duration.ofSeconds(timeout(zoneId)))
                .header("Accept", "application/json");
        String token = Token.valueIn(zoneId);
        if (StringUtils.isNotBlank(token)) {
            builder.header("Authorization", "Bearer " + token.trim());
        }
        return builder;
    }

    private int timeout(long zoneId) {
        Integer value = Timeout.valueIn(zoneId);
        return value == null || value <= 0 ? 10 : value;
    }

    protected synchronized HttpClient client(long zoneId) {
        if (client == null) {
            client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeout(zoneId))).build();
        }
        return client;
    }

    private HttpResponse<String> send(long zoneId, HttpRequest request) throws RouteAnnouncementException {
        try {
            return client(zoneId).send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new RouteAnnouncementException(request.uri().getHost() + " unreachable: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RouteAnnouncementException("interrupted while talking to " + request.uri().getHost(), e);
        }
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
