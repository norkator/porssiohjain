/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * Private self-hosting for personal household use is permitted.
 * Commercial use, resale, managed hosting, or offering the software as a
 * service to third parties requires separate written permission.
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.services.mitsubishihome;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MitsubishiMelCloudHomeApiClientTest {

    private HttpServer server;
    private MitsubishiMelCloudHomeApiClient client;
    private final AtomicReference<String> oauthState = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        URI baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        client = new MitsubishiMelCloudHomeApiClient(new ObjectMapper(), baseUri, baseUri);

        server.createContext("/connect/par", this::handlePar);
        server.createContext("/connect/authorize", exchange -> redirect(exchange, "/login?request=test"));
        server.createContext("/login", this::handleLogin);
        server.createContext("/callback", exchange -> redirect(
                exchange,
                "melcloudhome://callback?code=authorization-code&state=" + oauthState.get()
        ));
        server.createContext("/connect/token", this::handleToken);
        server.createContext("/context", this::handleContext);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void authenticatesWithPkceAndReadsAtaAndAtwContext() {
        MitsubishiMelCloudHomeApiClient.TokenResponse tokens = client.authenticate("user@example.com", "secret");

        assertEquals("access-token", tokens.accessToken());
        assertEquals("refresh-token", tokens.refreshToken());
        assertEquals(3600L, tokens.expiresInSeconds());

        MitsubishiMelCloudHomeContextResponse context = client.getContext(tokens.accessToken());

        assertEquals("user-1", context.id());
        assertEquals(1, context.buildings().size());
        assertEquals("ata-1", context.buildings().getFirst().airToAirUnits().getFirst().id());
        assertEquals("atw-1", context.buildings().getFirst().airToWaterUnits().getFirst().id());
    }

    @Test
    void refreshesAccessToken() {
        MitsubishiMelCloudHomeApiClient.TokenResponse tokens = client.refreshAccessToken("refresh-token");

        assertEquals("refreshed-access-token", tokens.accessToken());
        assertEquals("new-refresh-token", tokens.refreshToken());
    }

    private void handlePar(HttpExchange exchange) throws IOException {
        Map<String, String> form = readForm(exchange);
        oauthState.set(form.get("state"));
        assertEquals("homemobile", form.get("client_id"));
        assertEquals("S256", form.get("code_challenge_method"));
        assertTrue(form.get("code_challenge").length() >= 43);
        json(exchange, 200, "{\"request_uri\":\"urn:ietf:params:oauth:request_uri:test\"}");
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().add("Set-Cookie", "session=test; Path=/");
            html(exchange, 200, "<html><input name=\"_csrf\" value=\"csrf-token\"></html>");
            return;
        }
        Map<String, String> form = readForm(exchange);
        assertEquals("csrf-token", form.get("_csrf"));
        assertEquals("user@example.com", form.get("username"));
        assertEquals("secret", form.get("password"));
        assertTrue(exchange.getRequestHeaders().getFirst("Cookie").contains("session=test"));
        redirect(exchange, "/callback?code=authorization-code&state=" + oauthState.get());
    }

    private void handleToken(HttpExchange exchange) throws IOException {
        Map<String, String> form = readForm(exchange);
        if ("refresh_token".equals(form.get("grant_type"))) {
            assertEquals("refresh-token", form.get("refresh_token"));
            json(exchange, 200, """
                    {"access_token":"refreshed-access-token","refresh_token":"new-refresh-token","expires_in":3600}
                    """);
            return;
        }
        assertEquals("authorization_code", form.get("grant_type"));
        assertEquals("authorization-code", form.get("code"));
        assertTrue(form.get("code_verifier").length() >= 43);
        json(exchange, 200, """
                {"access_token":"access-token","refresh_token":"refresh-token","expires_in":3600}
                """);
    }

    private void handleContext(HttpExchange exchange) throws IOException {
        assertEquals("Bearer access-token", exchange.getRequestHeaders().getFirst("Authorization"));
        json(exchange, 200, """
                {
                  "id": "user-1",
                  "email": "user@example.com",
                  "buildings": [{
                    "id": "building-1",
                    "name": "Home",
                    "timezone": "Europe/Helsinki",
                    "airToAirUnits": [{"id":"ata-1","givenDisplayName":"Living room","isConnected":true}],
                    "airToWaterUnits": [{"id":"atw-1","givenDisplayName":"Hydronic heat pump","isConnected":true}]
                  }]
                }
                """);
    }

    private static Map<String, String> readForm(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> form = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            form.put(
                    URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts.length > 1 ? parts[1] : "", StandardCharsets.UTF_8)
            );
        }
        return form;
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private static void json(HttpExchange exchange, int status, String body) throws IOException {
        respond(exchange, status, "application/json", body);
    }

    private static void html(HttpExchange exchange, int status, String body) throws IOException {
        respond(exchange, status, "text/html", body);
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
