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

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLException;
import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.CookieHandler;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

/** Opt-in outbound transport: direct access first, with one proxy switch per operation. */
@Slf4j
@Component
public class OutboundProxyTransport {
    private static final ProxySelector DIRECT = new ProxySelector() {
        @Override
        public List<Proxy> select(URI uri) {
            return List.of(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, java.net.SocketAddress address, IOException failure) {
        }
    };

    private final OutboundProxyProperties properties;
    private final ClientHttpRequestFactory directFactory;
    private final ClientHttpRequestFactory proxyFactory;
    private final ThreadLocal<Operation> operation = new ThreadLocal<>();

    public OutboundProxyTransport(OutboundProxyProperties properties) {
        if (properties.getConnectTimeout() == null || properties.getConnectTimeout().isNegative()
                || properties.getConnectTimeout().isZero() || properties.getReadTimeout() == null
                || properties.getReadTimeout().isNegative() || properties.getReadTimeout().isZero()) {
            throw new IllegalArgumentException("Outbound proxy transport timeouts must be positive");
        }
        if (properties.isEnabled() && (properties.getHost() == null || properties.getHost().isBlank()
                || properties.getHost().contains("://") || properties.getPort() < 1 || properties.getPort() > 65535)) {
            throw new IllegalArgumentException("Enabled outbound proxy requires a host and a valid port");
        }
        this.properties = properties;
        this.directFactory = requestFactory(newHttpClient(null));
        this.proxyFactory = properties.isEnabled() ? requestFactory(newProxyClient(null)) : null;
    }

    public RestTemplate newRestTemplate() {
        RestTemplate template = new RestTemplate(directFactory);
        template.getInterceptors().add(this::intercept);
        return template;
    }

    public HttpClient newHttpClient(CookieHandler cookieHandler) {
        return clientBuilder(cookieHandler).proxy(DIRECT).build();
    }

    private HttpClient newProxyClient(CookieHandler cookieHandler) {
        return clientBuilder(cookieHandler)
                .proxy(ProxySelector.of(new InetSocketAddress(properties.getHost(), properties.getPort())))
                .build();
    }

    private HttpClient.Builder clientBuilder(CookieHandler cookieHandler) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout()).followRedirects(HttpClient.Redirect.NEVER);
        if (cookieHandler != null) {
            builder.cookieHandler(cookieHandler);
        }
        return builder;
    }

    private ClientHttpRequestFactory requestFactory(HttpClient client) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(properties.getReadTimeout());
        return factory;
    }

    /** Nested login/token refresh calls share the caller's route and retry budget. */
    public Scope beginOperation() {
        boolean owner = operation.get() == null;
        if (owner) {
            operation.set(new Operation());
        }
        return new Scope(owner);
    }

    public boolean isProxyRoute() {
        return operation.get() != null && operation.get().proxy;
    }

    public boolean hasFailedProxyAttempt() {
        return operation.get() != null && operation.get().failed;
    }

    public void markProxyFailure() {
        if (isProxyRoute()) {
            operation.get().failed = true;
        }
    }

    /** For protocol adapters, before any application payload has been submitted. */
    public boolean tryProxyAfterConnectionFailure(Throwable failure) {
        return tryProxyAfterConnectionFailure(failure, cause -> false);
    }

    /** Protocol adapters may recognize additional network failures wrapped by their SDK. */
    public boolean tryProxyAfterConnectionFailure(Throwable failure, Predicate<Throwable> additionalEligibleCause) {
        if (hasExcludedCause(failure)) {
            return false;
        }
        boolean eligible = isEligibleFailure(failure, true);
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (additionalEligibleCause.test(cause)) {
                eligible = true;
            }
        }
        return switchToProxy(eligible);
    }

    public Duration connectTimeout() {
        return properties.getConnectTimeout();
    }

    public Duration readTimeout() {
        return properties.getReadTimeout();
    }

    public Proxy httpProxy() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Outbound proxy is disabled");
        }
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(properties.getHost(), properties.getPort()));
    }

    private ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        try (Scope ignored = beginOperation()) {
            ensureOperationAvailable();
            if (!isProxyRoute()) {
                ClientHttpResponse response;
                try {
                    response = execution.execute(request, body);
                } catch (IOException failure) {
                    if (!switchToProxy(isEligibleFailure(failure, isReadOnly(request.getMethod().name())))) {
                        throw failure;
                    }
                    return sendProxy(request, body);
                }
                if (!switchToProxy(isForbidden(response.getStatusCode().value()))) {
                    return response;
                }
                response.close();
            }
            return sendProxy(request, body);
        }
    }

    private ClientHttpResponse sendProxy(HttpRequest request, byte[] body) throws IOException {
        try {
            var proxyRequest = proxyFactory.createRequest(request.getURI(), request.getMethod());
            proxyRequest.getHeaders().putAll(request.getHeaders());
            proxyRequest.getBody().write(body);
            ClientHttpResponse response = proxyRequest.execute();
            if (response.getStatusCode().isError()) {
                markProxyFailure();
            }
            return response;
        } catch (IOException failure) {
            markProxyFailure();
            throw failure;
        }
    }

    /** A login session reuses its cookies if it switches routes partway through OAuth. */
    public HttpResponse<String> send(HttpClient direct, java.net.http.HttpRequest request)
            throws IOException, InterruptedException {
        try (Scope ignored = beginOperation()) {
            ensureOperationAvailable();
            if (!isProxyRoute()) {
                HttpResponse<String> response;
                try {
                    response = direct.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                } catch (IOException failure) {
                    if (!switchToProxy(isEligibleFailure(failure, isReadOnly(request.method())))) {
                        throw failure;
                    }
                    return sendProxy(direct, request);
                }
                if (!switchToProxy(isForbidden(response.statusCode()))) {
                    return response;
                }
            }
            return sendProxy(direct, request);
        }
    }

    private HttpResponse<String> sendProxy(HttpClient direct, java.net.http.HttpRequest request)
            throws IOException, InterruptedException {
        try (HttpClient proxy = newProxyClient(direct.cookieHandler().orElse(null))) {
            HttpResponse<String> response = proxy.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                markProxyFailure();
            }
            return response;
        } catch (IOException | InterruptedException failure) {
            markProxyFailure();
            throw failure;
        }
    }

    private boolean switchToProxy(boolean eligible) {
        if (!eligible || !properties.isEnabled() || Thread.currentThread().isInterrupted()
                || operation.get() == null || isProxyRoute()) {
            return false;
        }
        operation.get().proxy = true;
        // No URLs, credentials, token-bearing query strings, request bodies, or exception messages.
        log.warn("Direct outbound request failed; attempting proxy fallback");
        return true;
    }

    private boolean isForbidden(int status) {
        return properties.isFallbackOnForbidden() && status == 403;
    }

    private void ensureOperationAvailable() throws IOException {
        if (hasFailedProxyAttempt()) {
            throw new IOException("Outbound proxy attempt failed; no further requests in this operation");
        }
    }

    private static boolean isReadOnly(String method) {
        return HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method);
    }

    static boolean isEligibleFailure(Throwable failure, boolean readOnly) {
        // Certificate failures and interruption must never be worked around by changing routes.
        if (hasExcludedCause(failure)) {
            return false;
        }
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof HttpConnectTimeoutException
                    || cause instanceof NoRouteToHostException || cause instanceof UnknownHostException) {
                return true;
            }
            if (readOnly && (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException
                    || cause instanceof SocketException || cause instanceof EOFException)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasExcludedCause(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SSLException || cause instanceof InterruptedException) {
                return true;
            }
        }
        return false;
    }

    private static class Operation {
        boolean proxy;
        boolean failed;
    }

    public final class Scope implements AutoCloseable {
        private final boolean owner;

        private Scope(boolean owner) {
            this.owner = owner;
        }

        @Override
        public void close() {
            if (owner) {
                operation.remove();
            }
        }
    }
}
