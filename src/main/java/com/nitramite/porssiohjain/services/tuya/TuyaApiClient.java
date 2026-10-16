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

/*
 * Tuya sharing protocol adapted from tuya/tuya-device-sharing-sdk (MIT).
 * Copyright (c) 2023 Tuya. See THIRD_PARTY_LICENSES/tuya-device-sharing-sdk.txt.
 */
package com.nitramite.porssiohjain.services.tuya;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
public class TuyaApiClient {
    static final String CLIENT_ID = "HA_3y9q4ak7g4ephrvke";
    static final String SCHEMA = "haauthorize";
    private static final String LOGIN_PATH = "/v1.0/m/life/home-assistant/qrcode/tokens";
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final URI loginBase;

    @Autowired
    public TuyaApiClient(ObjectMapper mapper) {
        this(mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER).build(), URI.create("https://apigw.iotbing.com"));
    }

    TuyaApiClient(ObjectMapper mapper, HttpClient http, URI loginBase) {
        this.mapper = mapper;
        this.http = http;
        this.loginBase = loginBase;
    }

    public String createQrCode(String userCode) {
        JsonNode response = send("POST", uri(loginBase + LOGIN_PATH, Map.of(
                "clientid", CLIENT_ID, "usercode", userCode, "schema", SCHEMA)), Map.of(), null);
        requireSuccess(response);
        return required(response.path("result"), "qrcode");
    }

    /** A non-success login response can mean the user has not approved the QR yet. */
    public JsonNode loginResult(String userCode, String qrToken) {
        JsonNode response = send("GET", uri(loginBase + LOGIN_PATH + "/" + encode(qrToken), Map.of(
                "clientid", CLIENT_ID, "usercode", userCode)), Map.of(), null);
        return response;
    }

    public TuyaSession sessionFromLogin(String userCode, JsonNode response) {
        requireSuccess(response);
        JsonNode result = response.path("result");
        String endpoint = required(result, "endpoint");
        validateEndpoint(endpoint);
        return new TuyaSession(userCode, endpoint.replaceAll("/+$", ""), required(result, "terminal_id"),
                required(result, "uid"), required(result, "access_token"), required(result, "refresh_token"),
                expiry(response, result, "expire_time"));
    }

    public TuyaSession refresh(TuyaSession session) {
        JsonNode response = request(session, "GET", "/v1.0/m/token/" + encode(session.refreshToken()), Map.of(), null);
        JsonNode result = response.path("result");
        if (!session.userId().equals(required(result, "uid"))) throw new TuyaException("tuya.error.accountChanged");
        return new TuyaSession(session.userCode(), session.endpoint(), session.terminalId(), session.userId(),
                required(result, "accessToken"), required(result, "refreshToken"), expiry(response, result, "expireTime"));
    }

    public JsonNode get(TuyaSession session, String path, Map<String, String> query) {
        return request(session, "GET", path, query, null).path("result");
    }

    public void revoke(TuyaSession session) {
        request(session, "POST", "/v1.0/m/token/terminal/expire", Map.of(), Map.of(
                "accessToken", session.accessToken(), "terminalId", session.terminalId()));
    }

    private JsonNode request(TuyaSession session, String method, String path, Map<String, String> query, Object body) {
        validateEndpoint(session.endpoint());
        String requestId = UUID.randomUUID().toString();
        String hashKey = TuyaSharingCodec.hashKey(requestId, session.refreshToken());
        String secret = TuyaSharingCodec.secret(requestId, hashKey);
        String encryptedQuery = query.isEmpty() ? "" : TuyaSharingCodec.encrypt(json(query), secret);
        String encryptedBody = body == null ? "" : TuyaSharingCodec.encrypt(json(body), secret);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-appKey", CLIENT_ID);
        headers.put("X-requestId", requestId);
        headers.put("X-sid", "");
        headers.put("X-time", Long.toString(System.currentTimeMillis()));
        headers.put("X-token", session.accessToken());
        headers.put("X-sign", TuyaSharingCodec.sign(hashKey, headers, encryptedQuery, encryptedBody));
        JsonNode response = send(method, uri(session.endpoint() + path,
                        encryptedQuery.isEmpty() ? Map.of() : Map.of("encdata", encryptedQuery)), headers,
                encryptedBody.isEmpty() ? null : json(Map.of("encdata", encryptedBody)));
        requireSuccess(response);
        JsonNode result = response.path("result");
        if (result.isTextual() && !result.asText().isEmpty()) {
            String decrypted = TuyaSharingCodec.decrypt(result.asText(), secret);
            try {
                ((ObjectNode) response).set("result", mapper.readTree(decrypted));
            } catch (Exception e) {
                ((ObjectNode) response).put("result", decrypted);
            }
        } else if (!result.isNull() && !result.isMissingNode() && !result.isTextual()
                && !(result.isContainerNode() && result.isEmpty())
                && !(result.isBoolean() && !result.booleanValue()) && !(result.isNumber() && result.asDouble() == 0)) {
            throw new TuyaException("tuya.error.response");
        }
        return response;
    }

    private JsonNode send(String method, URI uri, Map<String, String> headers, String body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json");
            headers.forEach(request::header);
            if (body != null) request.header("Content-Type", "application/json");
            request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new TuyaException("tuya.error.request", "HTTP_" + response.statusCode());
            JsonNode json = mapper.readTree(response.body());
            if (json == null || !json.isObject()) throw new TuyaException("tuya.error.response");
            return json;
        } catch (TuyaException e) { throw e; }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TuyaException("tuya.error.request", "INTERRUPTED");
        } catch (Exception e) { throw new TuyaException("tuya.error.request", "NETWORK_OR_RESPONSE"); }
    }

    static void validateEndpoint(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            if (!"https".equals(uri.getScheme()) || host == null || uri.getUserInfo() != null || uri.getPort() != -1
                    || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))
                    || List.of("tuyaeu.com", "tuyaus.com", "tuyacn.com", "tuyain.com", "iotbing.com").stream()
                    .noneMatch(domain -> host.equals(domain) || host.endsWith("." + domain)))
                throw new IllegalArgumentException();
        } catch (Exception e) { throw new TuyaException("tuya.error.response"); }
    }

    private Instant expiry(JsonNode response, JsonNode result, String field) {
        long seconds = result.path(field).asLong(0);
        long timestamp = response.path("t").asLong(0);
        if (seconds <= 0 || seconds > 31_536_000L || timestamp <= 0) throw new TuyaException("tuya.error.response");
        return Instant.ofEpochMilli(timestamp).plusSeconds(seconds);
    }

    static String required(JsonNode node, String key) {
        String value = node.path(key).asText("");
        if (value.isBlank()) throw new TuyaException("tuya.error.response");
        return value;
    }

    private void requireSuccess(JsonNode response) {
        if (!response.path("success").asBoolean(false))
            throw new TuyaException("tuya.error.request", response.path("code").asText(""));
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { throw new TuyaException("tuya.error.response"); }
    }

    private static URI uri(String base, Map<String, String> query) {
        StringJoiner params = new StringJoiner("&");
        query.forEach((key, value) -> params.add(encode(key) + "=" + encode(value)));
        return URI.create(base + (query.isEmpty() ? "" : "?" + params));
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
