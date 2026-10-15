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

package com.nitramite.porssiohjain.services.tuya;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TuyaApiClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = mock(HttpClient.class);
    private final TuyaApiClient api = new TuyaApiClient(mapper, http, URI.create("https://apigw.iotbing.com"));

    @Test
    void transportMatchesVectorsGeneratedByOfficialPythonSdk() {
        // SDK 0.2.15 customerapi.py helpers, fixed request ID and nonce, independently run with Python cryptography.
        String rid = "00000000-1111-2222-3333-444444444444";
        String hash = TuyaSharingCodec.hashKey(rid, "refresh-token");
        assertEquals("4d0612b4f978206dcc1734143824072e", hash);
        String secret = TuyaSharingCodec.secret(rid, hash);
        assertEquals("2d0094c8347169f5", secret);
        String encrypted = "YWJjZGVmZ2hpamtsLIZk2fNr2bc8KgNOSLSiywCYAtpNCIECMeCfYRCe/pP75w==";
        assertEquals(encrypted, TuyaSharingCodec.encrypt("{\"homeId\":\"12345\"}", secret, "abcdefghijkl".getBytes(StandardCharsets.UTF_8)));
        assertEquals("{\"homeId\":\"12345\"}", TuyaSharingCodec.decrypt(encrypted, secret));
        assertEquals("4b4a1b3b93fc6f7f9c3aa0a0e9d95960a9dbc52313d53ab0ee42cf66e577c5f1", TuyaSharingCodec.sign(hash,
                Map.of("X-appKey", TuyaApiClient.CLIENT_ID, "X-requestId", rid, "X-sid", "",
                        "X-time", "1792062000000", "X-token", "access-token"), encrypted, ""));
        assertThrows(TuyaException.class, () -> TuyaSharingCodec.decrypt(encrypted.replace("LIZk", "MIZk"), secret));
    }

    @Test
    void startsQrLoginUsingPublicRegistrationAndEncodedUserCode() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            HttpRequest request = call.getArgument(0);
            assertEquals("POST", request.method());
            assertTrue(request.uri().getRawQuery().contains("schema=haauthorize"));
            assertTrue(request.uri().getRawQuery().contains("usercode=abc%2B123"));
            assertTrue(request.headers().firstValue("X-token").isEmpty());
            return response("{\"success\":true,\"result\":{\"qrcode\":\"qr-token\"}}", 200);
        });
        assertEquals("qr-token", api.createQrCode("abc+123"));
    }

    @Test
    void encryptsDiscoveryParametersAndDecryptsResponse() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            HttpRequest request = call.getArgument(0);
            String secret = secret(request, "refresh-token");
            assertEquals("access-token", request.headers().firstValue("X-token").orElseThrow());
            assertEquals("/v1.0/m/life/ha/home/devices", request.uri().getPath());
            String query = URLDecoder.decode(request.uri().getRawQuery().substring("encdata=".length()), StandardCharsets.UTF_8);
            assertEquals("12345", mapper.readTree(TuyaSharingCodec.decrypt(query, secret)).path("homeId").asText());
            return encryptedResponse(request, "refresh-token", "[{\"id\":\"sensor-1\"}]");
        });
        var result = api.get(session(), "/v1.0/m/life/ha/home/devices", Map.of("homeId", "12345"));
        assertEquals("sensor-1", result.get(0).path("id").asText());
    }

    @Test
    void refreshesUsingOldCredentialsAndParsesRotatedTokensAndServerExpiry() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            HttpRequest request = call.getArgument(0);
            assertEquals("/v1.0/m/token/refresh-token", request.uri().getPath());
            return encryptedResponse(request, "refresh-token", """
                    {"uid":"user-1","accessToken":"rotated-access","refreshToken":"rotated-refresh","expireTime":3600}
                    """);
        });
        TuyaSession refreshed = api.refresh(session());
        assertEquals("rotated-access", refreshed.accessToken());
        assertEquals("rotated-refresh", refreshed.refreshToken());
        assertEquals(Instant.ofEpochMilli(1792062000000L).plusSeconds(3600), refreshed.expiresAt());
        assertFalse(refreshed.toString().contains("rotated"));
    }

    @Test
    void handlesEmptyRevocationAndRedactsProviderErrors() throws Exception {
        HttpResponse<String> empty = response("{\"success\":true,\"result\":null}", 200);
        HttpResponse<String> failed = response("{\"success\":false,\"code\":1010,\"msg\":\"private-access-token\"}", 200);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(empty).thenReturn(failed);
        assertDoesNotThrow(() -> api.revoke(session()));
        TuyaException error = assertThrows(TuyaException.class, () -> api.get(session(), "/homes", Map.of()));
        assertEquals("1010", error.code());
        assertFalse(error.getMessage().contains("private"));
    }

    @Test
    void rejectsUntrustedLoginEndpointsBeforeTokensCanBeSent() {
        for (String endpoint : new String[]{"http://apigw.tuyaeu.com", "https://apigw.tuyaeu.com.evil.test",
                "https://127.0.0.1", "https://user@apigw.tuyaeu.com", "https://apigw.tuyaeu.com:8443",
                "https://apigw.tuyaeu.com/path", "https://apigw.tuyaeu.com?target=x"})
            assertThrows(TuyaException.class, () -> TuyaApiClient.validateEndpoint(endpoint), endpoint);
        assertDoesNotThrow(() -> TuyaApiClient.validateEndpoint("https://apigw-weaz.tuyaeu.com"));
    }

    private TuyaSession session() {
        return new TuyaSession("code", "https://apigw.tuyaeu.com", "terminal", "user-1",
                "access-token", "refresh-token", Instant.now().plusSeconds(3600));
    }

    private String secret(HttpRequest request, String refreshToken) {
        String id = request.headers().firstValue("X-requestId").orElseThrow();
        return TuyaSharingCodec.secret(id, TuyaSharingCodec.hashKey(id, refreshToken));
    }

    private HttpResponse<String> encryptedResponse(HttpRequest request, String refreshToken, String result) throws Exception {
        return response(mapper.writeValueAsString(Map.of("success", true, "t", 1792062000000L,
                "result", TuyaSharingCodec.encrypt(result, secret(request, refreshToken)))), 200);
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(String json, int status) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(json);
        return response;
    }
}
