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
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.network.routedsubnet.Announcement;
import org.apache.cloudstack.network.routedsubnet.RouteAnnouncementException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class HttpRouteAnnouncerTest {

    private HttpServer server;
    private HttpRouteAnnouncer announcer;
    private final List<String> requests = new ArrayList<>();
    private String routesBody = "[{\"prefix\": \"203.0.113.0/29\", \"next_hop\": \"192.0.2.10\"}]";
    private int putStatus = 200;

    @Before
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        announcer = new HttpRouteAnnouncer();
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
        override(HttpRouteAnnouncer.Endpoints, endpoint + "/, " + endpoint);
        override(HttpRouteAnnouncer.Token, "secret");
    }

    @After
    public void tearDown() {
        server.stop(0);
    }

    private static void override(ConfigKey<?> key, String value) throws Exception {
        Field f = ConfigKey.class.getDeclaredField("_defaultValue");
        f.setAccessible(true);
        f.set(key, value);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body;
        try (InputStream in = exchange.getRequestBody()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " " + body);
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        int status;
        String response;
        if (!"Bearer secret".equals(auth)) {
            status = 401;
            response = "{\"error\": \"unauthorized\"}";
        } else if (exchange.getRequestURI().getPath().equals("/v1/health")) {
            status = 200;
            response = "{\"status\": \"ok\", \"instance\": \"abc\", \"routes\": 1}";
        } else if (exchange.getRequestURI().getPath().equals("/v1/routes")) {
            status = 200;
            response = routesBody;
        } else if ("PUT".equals(exchange.getRequestMethod())) {
            status = putStatus;
            response = "{}";
        } else if ("DELETE".equals(exchange.getRequestMethod())) {
            status = 404;
            response = "{\"error\": \"not held\"}";
        } else {
            status = 500;
            response = "{}";
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Test
    public void endpointsAreTrimmedAndDeduplicatedOnlyBySpelling() {
        List<String> endpoints = announcer.getEndpoints(1L);
        Assert.assertEquals(2, endpoints.size());
        Assert.assertFalse(endpoints.get(0).endsWith("/"));
        Assert.assertEquals(endpoints.get(0), endpoints.get(1));
    }

    @Test
    public void announceEncodesThePrefixAndSendsTheNextHop() throws Exception {
        String endpoint = announcer.getEndpoints(1L).get(0);
        announcer.announce(1L, endpoint, "203.0.113.0/29", "192.0.2.10");
        Assert.assertEquals("PUT /v1/routes/203.0.113.0%2F29 {\"next_hop\":\"192.0.2.10\"}", requests.get(0));
    }

    @Test(expected = RouteAnnouncementException.class)
    public void rejectedAnnouncementThrows() throws Exception {
        putStatus = 400;
        announcer.announce(1L, announcer.getEndpoints(1L).get(0), "203.0.113.0/29", "192.0.2.10");
    }

    @Test
    public void withdrawTreatsNotHeldAsDone() throws Exception {
        announcer.withdraw(1L, announcer.getEndpoints(1L).get(0), "203.0.113.0/29", "192.0.2.10");
        Assert.assertEquals("DELETE /v1/routes/203.0.113.0%2F29?next_hop=192.0.2.10 ", requests.get(0));
    }

    @Test
    public void listAndHealth() throws Exception {
        String endpoint = announcer.getEndpoints(1L).get(0);
        Set<Announcement> routes = announcer.list(1L, endpoint);
        Assert.assertEquals(1, routes.size());
        Assert.assertTrue(routes.contains(new Announcement("203.0.113.0/29", "192.0.2.10")));
        Assert.assertEquals("abc", announcer.getInstanceId(1L, endpoint));
    }

    @Test(expected = RouteAnnouncementException.class)
    public void wrongTokenIsAnError() throws Exception {
        override(HttpRouteAnnouncer.Token, "other");
        announcer.list(1L, announcer.getEndpoints(1L).get(0));
    }

    @Test(expected = RouteAnnouncementException.class)
    public void unreachableEndpointIsAnError() throws Exception {
        announcer.getInstanceId(1L, "http://127.0.0.1:1");
    }
}
