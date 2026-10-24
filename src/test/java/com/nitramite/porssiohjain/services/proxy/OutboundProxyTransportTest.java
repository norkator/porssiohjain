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

package com.nitramite.porssiohjain.services.proxy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;

class OutboundProxyTransportTest {
    private HttpServer origin;
    private HttpServer proxy;
    private URI uri;
    private OutboundProxyProperties properties;
    private final AtomicInteger originStatus = new AtomicInteger(200);
    private final AtomicInteger proxyStatus = new AtomicInteger(200);
    private final AtomicInteger directRequests = new AtomicInteger();
    private final AtomicInteger proxyRequests = new AtomicInteger();
    private final AtomicReference<String> proxyBody = new AtomicReference<>();
    private final AtomicReference<String> proxyAuthorization = new AtomicReference<>();
    private final AtomicReference<URI> proxyTarget = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        origin = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        proxy = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        origin.createContext("/test", exchange -> {
            directRequests.incrementAndGet();
            respond(exchange, originStatus.get(), "direct");
        });
        proxy.createContext("/", exchange -> {
            proxyRequests.incrementAndGet();
            proxyBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            proxyAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            proxyTarget.set(exchange.getRequestURI());
            respond(exchange, proxyStatus.get(), "proxy");
        });
        origin.start();
        proxy.start();
        uri = URI.create("http://127.0.0.1:" + origin.getAddress().getPort() + "/test");
        properties = new OutboundProxyProperties();
        properties.setEnabled(true);
        properties.setHost("127.0.0.1");
        properties.setPort(proxy.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        origin.stop(0);
        proxy.stop(0);
    }

    @Test
    void directSuccessNeverContactsProxy() {
        assertThat(client().getForObject(uri, String.class)).isEqualTo("direct");
        assertThat(directRequests).hasValue(1);
        assertThat(proxyRequests).hasValue(0);
    }

    @Test
    void forbiddenRetriesThroughProxyAndNextCallStartsDirectAgain() {
        RestTemplate client = client();
        originStatus.set(403);
        assertThat(client.getForObject(uri, String.class)).isEqualTo("proxy");
        assertThat(proxyTarget.get()).isEqualTo(uri);
        originStatus.set(200);
        assertThat(client.getForObject(uri, String.class)).isEqualTo("direct");
        assertThat(directRequests).hasValue(2);
        assertThat(proxyRequests).hasValue(1);
    }

    @Test
    void explicitCommandRejectionCanRetryWithIdenticalBodyAndHeaders() {
        originStatus.set(403);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("test-token");
        assertThat(client().postForObject(uri, new HttpEntity<>("set temperature 21", headers), String.class))
                .isEqualTo("proxy");
        assertThat(proxyBody).hasValue("set temperature 21");
        assertThat(proxyAuthorization).hasValue("Bearer test-token");
        assertThat(directRequests).hasValue(1);
        assertThat(proxyRequests).hasValue(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 404, 429, 500, 503})
    void authenticationRateLimitsAndOtherErrorsDoNotUseProxy(int status) {
        originStatus.set(status);
        assertThatThrownBy(() -> client().getForObject(uri, String.class)).isInstanceOf(RestClientException.class);
        assertThat(directRequests).hasValue(1);
        assertThat(proxyRequests).hasValue(0);
    }

    @Test
    void disabledFallbackAndForbiddenOptOutRemainDirect() {
        originStatus.set(403);
        properties.setEnabled(false);
        assertThatThrownBy(() -> client().getForObject(uri, String.class)).isInstanceOf(RestClientException.class);
        properties.setEnabled(true);
        properties.setFallbackOnForbidden(false);
        assertThatThrownBy(() -> client().getForObject(uri, String.class)).isInstanceOf(RestClientException.class);
        assertThat(proxyRequests).hasValue(0);
    }

    @Test
    void failedProxyStopsNestedRequestsAndScopeIsClearedForNextOperation() {
        originStatus.set(403);
        proxyStatus.set(403);
        OutboundProxyTransport transport = new OutboundProxyTransport(properties);
        RestTemplate client = transport.newRestTemplate();
        try (var ignored = transport.beginOperation()) {
            assertThatThrownBy(() -> client.getForObject(uri, String.class)).isInstanceOf(RestClientException.class);
            // A nested login or token refresh must not start another fallback loop.
            assertThatThrownBy(() -> client.getForObject(uri, String.class)).isInstanceOf(RestClientException.class);
        }
        originStatus.set(200);
        assertThat(client.getForObject(uri, String.class)).isEqualTo("direct");
        assertThat(directRequests).hasValue(2);
        assertThat(proxyRequests).hasValue(1);
    }

    @Test
    void readTimeoutRetriesReadButNeverReplaysAnUncertainCommand() {
        assertFallbackForFailure(HttpMethod.GET, new HttpTimeoutException("read timeout"), true);
        assertFallbackForFailure(HttpMethod.POST, new HttpTimeoutException("read timeout"), false);
        assertFallbackForFailure(HttpMethod.PUT, new SocketTimeoutException("read timeout"), false);
        assertFallbackForFailure(HttpMethod.POST, new SocketException("connection reset"), false);
    }

    @Test
    void connectTimeoutCanRetryCommandAndCertificateErrorsNeverFallback() {
        assertFallbackForFailure(HttpMethod.POST, new HttpConnectTimeoutException("connect timeout"), true);
        assertFallbackForFailure(HttpMethod.GET, new IOException(new SSLHandshakeException("untrusted")), false);
        assertFallbackForFailure(HttpMethod.POST, new IOException("unknown failure"), false);
    }

    @Test
    void missingEnabledProxyAddressFailsConfiguration() {
        properties.setHost("");
        assertThatThrownBy(() -> new OutboundProxyTransport(properties)).isInstanceOf(IllegalArgumentException.class);
    }

    private void assertFallbackForFailure(HttpMethod method, IOException failure, boolean expected) {
        RestTemplate client = client();
        MockRestServiceServer mockDirect = MockRestServiceServer.bindTo(client).build();
        mockDirect.expect(requestTo(uri)).andRespond(withException(failure));
        int previousProxyRequests = proxyRequests.get();
        if (expected) {
            assertThat(client.exchange(uri, method, HttpEntity.EMPTY, String.class).getBody()).isEqualTo("proxy");
        } else {
            assertThatThrownBy(() -> client.exchange(uri, method, HttpEntity.EMPTY, String.class))
                    .isInstanceOf(RestClientException.class);
        }
        assertThat(proxyRequests).hasValue(previousProxyRequests + (expected ? 1 : 0));
        mockDirect.verify();
    }

    private RestTemplate client() {
        return new OutboundProxyTransport(properties).newRestTemplate();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
