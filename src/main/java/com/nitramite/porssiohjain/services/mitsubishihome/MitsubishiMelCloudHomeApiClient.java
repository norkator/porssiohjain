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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class MitsubishiMelCloudHomeApiClient {

    static final URI DEFAULT_AUTH_BASE = URI.create("https://auth.melcloudhome.com");
    static final URI DEFAULT_API_BASE = URI.create("https://mobile.bff.melcloudhome.com");

    private static final String CLIENT_ID = "homemobile";
    private static final String REDIRECT_URI = "melcloudhome://";
    private static final String SCOPES = "openid profile email offline_access IdentityServerApi";
    private static final int MAX_REDIRECTS = 10;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final Pattern CSRF_PATTERN = Pattern.compile("name=[\"']_csrf[\"']\\s+value=[\"']([^\"']+)[\"']");

    private final ObjectMapper objectMapper;
    private final URI authBase;
    private final URI apiBase;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public MitsubishiMelCloudHomeApiClient(ObjectMapper objectMapper) {
        this(objectMapper, DEFAULT_AUTH_BASE, DEFAULT_API_BASE);
    }

    MitsubishiMelCloudHomeApiClient(ObjectMapper objectMapper, URI authBase, URI apiBase) {
        this.objectMapper = objectMapper;
        this.authBase = authBase;
        this.apiBase = apiBase;
    }

    public TokenResponse authenticate(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new MitsubishiMelCloudHomeException("MELCloud Home username and password are required");
        }

        String verifier = generateCodeVerifier();
        String challenge = createCodeChallenge(verifier);
        String state = generateUrlSafeRandom(16);
        HttpClient client = newAuthenticationClient();

        JsonNode parResponse = readJson(sendForm(client, authUri("/connect/par"), Map.of(
                "client_id", CLIENT_ID,
                "redirect_uri", REDIRECT_URI,
                "response_type", "code",
                "scope", SCOPES,
                "state", state,
                "code_challenge", challenge,
                "code_challenge_method", "S256"
        )));
        String requestUri = requiredText(parResponse, "request_uri", "PAR response did not contain request_uri");

        URI authorizeUri = withQuery(authUri("/connect/authorize"), Map.of(
                "client_id", CLIENT_ID,
                "request_uri", requestUri
        ));
        HttpResponse<String> loginPage = followHttpGetRedirects(client, authorizeUri);
        requireSuccess(loginPage, "Loading the MELCloud Home login page failed");

        Matcher csrfMatcher = CSRF_PATTERN.matcher(loginPage.body());
        if (!csrfMatcher.find()) {
            throw new MitsubishiMelCloudHomeException("MELCloud Home login page did not contain a CSRF token");
        }

        HttpResponse<String> credentialResponse = sendForm(client, loginPage.uri(), Map.of(
                "_csrf", csrfMatcher.group(1),
                "username", username,
                "password", password
        ));
        if (!isRedirect(credentialResponse.statusCode())) {
            throw new MitsubishiMelCloudHomeException(
                    "MELCloud Home rejected the credentials (HTTP " + credentialResponse.statusCode() + ")"
            );
        }

        URI callbackUri = followCallbackRedirects(client, credentialResponse);
        Map<String, String> callbackParameters = parseQuery(callbackUri.getRawQuery());
        String authorizationCode = callbackParameters.get("code");
        if (authorizationCode == null || authorizationCode.isBlank()) {
            throw new MitsubishiMelCloudHomeException("MELCloud Home callback did not contain an authorization code");
        }
        String returnedState = callbackParameters.get("state");
        if (returnedState != null && !returnedState.equals(state)) {
            throw new MitsubishiMelCloudHomeException("MELCloud Home callback state did not match the login request");
        }

        return exchangeToken(client, Map.of(
                "grant_type", "authorization_code",
                "client_id", CLIENT_ID,
                "code", authorizationCode,
                "redirect_uri", REDIRECT_URI,
                "code_verifier", verifier
        ));
    }

    public TokenResponse refreshAccessToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new MitsubishiMelCloudHomeException("MELCloud Home refresh token is required");
        }
        return exchangeToken(newAuthenticationClient(), Map.of(
                "grant_type", "refresh_token",
                "client_id", CLIENT_ID,
                "refresh_token", refreshToken
        ));
    }

    public MitsubishiMelCloudHomeContextResponse getContext(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new MitsubishiMelCloudHomeException("MELCloud Home access token is required");
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpRequest request = HttpRequest.newBuilder(apiUri("/context"))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .header("User-Agent", "Porssiohjain MELCloud Home")
                .GET()
                .build();
        HttpResponse<String> response = send(client, request);
        requireSuccess(response, "Fetching MELCloud Home context failed");
        try {
            return objectMapper.readValue(response.body(), MitsubishiMelCloudHomeContextResponse.class);
        } catch (IOException e) {
            throw new MitsubishiMelCloudHomeException("Unable to parse MELCloud Home context response", e);
        }
    }

    private TokenResponse exchangeToken(HttpClient client, Map<String, String> parameters) {
        JsonNode tokenResponse = readJson(sendForm(client, authUri("/connect/token"), parameters));
        String accessToken = requiredText(tokenResponse, "access_token", "Token response did not contain access_token");
        String refreshToken = tokenResponse.path("refresh_token").asText(null);
        long expiresIn = tokenResponse.path("expires_in").asLong(3600L);
        return new TokenResponse(accessToken, refreshToken, expiresIn);
    }

    private HttpResponse<String> followHttpGetRedirects(HttpClient client, URI startUri) {
        URI currentUri = startUri;
        for (int redirect = 0; redirect < MAX_REDIRECTS; redirect++) {
            HttpResponse<String> response = sendGet(client, currentUri);
            if (!isRedirect(response.statusCode())) {
                return response;
            }
            currentUri = redirectLocation(response);
            if (!isHttpUri(currentUri)) {
                return response;
            }
        }
        throw new MitsubishiMelCloudHomeException("MELCloud Home login exceeded the redirect limit");
    }

    private URI followCallbackRedirects(HttpClient client, HttpResponse<String> startResponse) {
        HttpResponse<String> response = startResponse;
        URI baseUri = response.uri();
        for (int redirect = 0; redirect < MAX_REDIRECTS; redirect++) {
            String location = response.headers().firstValue("Location").orElse(null);
            if (location == null || location.isBlank()) {
                String redirectUri = parseQuery(baseUri.getRawQuery()).get("RedirectUri");
                if (redirectUri == null || redirectUri.isBlank()) {
                    throw new MitsubishiMelCloudHomeException("MELCloud Home callback redirect was missing");
                }
                location = redirectUri;
            }

            URI nextUri = resolve(baseUri, location);
            if (!isHttpUri(nextUri)) {
                return nextUri;
            }
            response = sendGet(client, nextUri);
            baseUri = nextUri;
        }
        throw new MitsubishiMelCloudHomeException("MELCloud Home callback exceeded the redirect limit");
    }

    private HttpResponse<String> sendForm(HttpClient client, URI uri, Map<String, String> parameters) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json, text/html")
                .header("User-Agent", "Porssiohjain MELCloud Home")
                .POST(HttpRequest.BodyPublishers.ofString(formEncode(parameters)))
                .build();
        return send(client, request);
    }

    private HttpResponse<String> sendGet(HttpClient client, URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "text/html, application/json")
                .header("User-Agent", "Porssiohjain MELCloud Home")
                .GET()
                .build();
        return send(client, request);
    }

    private HttpResponse<String> send(HttpClient client, HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MitsubishiMelCloudHomeException("MELCloud Home request was interrupted", e);
        } catch (IOException | IllegalArgumentException e) {
            throw new MitsubishiMelCloudHomeException("MELCloud Home request failed", e);
        }
    }

    private JsonNode readJson(HttpResponse<String> response) {
        requireSuccess(response, "MELCloud Home request failed");
        try {
            return objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new MitsubishiMelCloudHomeException("Unable to parse MELCloud Home response", e);
        }
    }

    private void requireSuccess(HttpResponse<String> response, String message) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("{}: status={}", message, response.statusCode());
            throw new MitsubishiMelCloudHomeException(message + " (HTTP " + response.statusCode() + ")");
        }
    }

    private HttpClient newAuthenticationClient() {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        return HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .connectTimeout(REQUEST_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private URI redirectLocation(HttpResponse<String> response) {
        String location = response.headers().firstValue("Location")
                .orElseThrow(() -> new MitsubishiMelCloudHomeException("MELCloud Home redirect was missing Location"));
        return resolve(response.uri(), location);
    }

    private URI authUri(String path) {
        return authBase.resolve(path);
    }

    private URI apiUri(String path) {
        return apiBase.resolve(path);
    }

    private static URI resolve(URI base, String location) {
        URI locationUri = URI.create(location);
        return locationUri.isAbsolute() ? locationUri : base.resolve(locationUri);
    }

    private static URI withQuery(URI uri, Map<String, String> parameters) {
        return URI.create(uri + "?" + formEncode(parameters));
    }

    private static boolean isRedirect(int statusCode) {
        return statusCode >= 300 && statusCode < 400;
    }

    private static boolean isHttpUri(URI uri) {
        return "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
    }

    private static String formEncode(Map<String, String> parameters) {
        return parameters.entrySet().stream()
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> values = new LinkedHashMap<>();
        if (query == null || query.isBlank()) {
            return values;
        }
        for (String pair : query.split("&")) {
            int separator = pair.indexOf('=');
            String key = separator >= 0 ? pair.substring(0, separator) : pair;
            String value = separator >= 0 ? pair.substring(separator + 1) : "";
            values.put(
                    java.net.URLDecoder.decode(key, StandardCharsets.UTF_8),
                    java.net.URLDecoder.decode(value, StandardCharsets.UTF_8)
            );
        }
        return values;
    }

    private String generateCodeVerifier() {
        return generateUrlSafeRandom(32);
    }

    private String generateUrlSafeRandom(int bytes) {
        byte[] randomBytes = new byte[bytes];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private static String createCodeChallenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String requiredText(JsonNode node, String fieldName, String message) {
        String value = node.path(fieldName).asText(null);
        if (value == null || value.isBlank()) {
            throw new MitsubishiMelCloudHomeException(message);
        }
        return value;
    }

    public record TokenResponse(String accessToken, String refreshToken, long expiresInSeconds) {
    }
}
