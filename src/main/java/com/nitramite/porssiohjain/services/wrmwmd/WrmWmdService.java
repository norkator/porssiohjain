/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.services.wrmwmd;

import com.nitramite.porssiohjain.entity.enums.WaterMeterProvider;
import com.nitramite.porssiohjain.services.watermeters.WaterMeterClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Pattern;

@Service
public class WrmWmdService implements WaterMeterClient {
    static final URI ORIGIN = URI.create("https://wmd.wrm-systems.fi");
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final Pattern VALUE = Pattern.compile("([0-9]+(?:[,.][0-9]+)?)m[3³]");
    private final URI origin;

    public WrmWmdService() {
        this(ORIGIN);
    }

    WrmWmdService(URI origin) {
        this.origin = origin;
    }

    @Override
    public WaterMeterProvider provider() {
        return WaterMeterProvider.WRM_SYSTEMS_WMD;
    }

    /** A fresh, isolated cookie jar per login prevents sessions from mixing between meters/accounts. */
    public Reading readLatest(String portalUrl, String payerNumber, String waterMeterNumber) {
        String tenant = tenant(portalUrl);
        requireCredential(payerNumber);
        requireCredential(waterMeterNumber);
        HttpClient client = newClient();
        HttpResponse<String> loginPage = get(client, origin.resolve("/" + tenant));
        requireSuccess(loginPage);
        Document page = Jsoup.parse(loginPage.body());
        Element csrf = page.selectFirst("meta[name=csrf-token]");
        if (csrf == null || csrf.attr("content").isBlank()) throw error("response");

        StringJoiner form = new StringJoiner("&");
        Map.of("_csrf", csrf.attr("content"), "mode", "water", "login-by", "ul",
                "login-input-a", payerNumber, "login-input-b", waterMeterNumber)
                .forEach((key, value) -> form.add(encode(key) + "=" + encode(value)));
        HttpResponse<String> login = send(client, HttpRequest.newBuilder(origin.resolve("/" + tenant + "/login"))
                .timeout(TIMEOUT).header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", origin.toString()).header("Referer", origin.resolve("/" + tenant).toString())
                .POST(HttpRequest.BodyPublishers.ofString(form.toString())).build());
        // Yii normally redirects after login. Do not follow redirects or send cookies to another origin.
        if (login.statusCode() == 401 || login.statusCode() == 403) throw error("login");
        if (login.statusCode() < 200 || login.statusCode() >= 400) throw error("response");
        if (isLoginPage(login.body())) throw error("login");
        return readCard(client, null);
    }

    /** For inspecting an existing browser session; normal daily polling uses stored login credentials. */
    public Reading readWithSession(String sessionId) {
        if (sessionId == null || !sessionId.matches("[A-Za-z0-9,-]{1,256}")) throw error("credentials");
        return readCard(newClient(), sessionId);
    }

    private Reading readCard(HttpClient client, String sessionId) {
        HttpRequest.Builder request = HttpRequest.newBuilder(origin.resolve("/cards/watermeter-info"))
                .timeout(TIMEOUT).header("Accept", "text/html");
        if (sessionId != null) request.header("Cookie", "sessionId=" + sessionId);
        HttpResponse<String> response = send(client, request.GET().build());
        if (response.statusCode() >= 300 && response.statusCode() < 400
                || response.statusCode() == 401 || response.statusCode() == 403
                || isLoginPage(response.body())) throw error("login");
        requireSuccess(response);
        return new Reading(parseCubicMeters(response.body()), Instant.now());
    }

    static BigDecimal parseCubicMeters(String html) {
        Document document = Jsoup.parse(html);
        BigDecimal reading = null;
        for (Element row : document.select("div.row")) {
            var cells = row.children().stream().filter(cell -> cell.hasClass("col")).toList();
            if (cells.size() != 2 || !cells.getFirst().text().strip().equals("Viimeisin lukema")) continue;
            if (reading != null) throw error("response"); // Never silently choose between multiple meters.
            String text = cells.get(1).text().replaceAll("[\\s\\p{Zs}]+", "");
            var match = VALUE.matcher(text);
            if (!match.matches()) throw error("response");
            reading = new BigDecimal(match.group(1).replace(',', '.'));
            if (reading.scale() > 6 || reading.precision() - reading.scale() > 14) throw error("response");
        }
        if (reading == null) throw error("response");
        return reading;
    }

    @Override
    public String normalizePortalUrl(String portalUrl) {
        return ORIGIN.resolve("/" + tenant(portalUrl)).toString();
    }

    private static String tenant(String portalUrl) {
        try {
            URI uri = URI.create(portalUrl == null ? "" : portalUrl.strip());
            if (!"https".equals(uri.getScheme()) || !ORIGIN.getHost().equals(uri.getHost())
                    || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getQuery() != null
                    || uri.getFragment() != null || !uri.getPath().matches("/[a-z0-9-]{1,64}/?")) throw error("portal");
            return uri.getPath().replace("/", "");
        } catch (IllegalArgumentException exception) {
            throw error("portal");
        }
    }

    private static void requireCredential(String value) {
        if (value == null || value.isBlank() || value.length() > 30
                || value.chars().anyMatch(Character::isISOControl)) throw error("credentials");
    }

    private HttpClient newClient() {
        return HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NEVER)
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)).build();
    }

    private HttpResponse<String> get(HttpClient client, URI uri) {
        return send(client, HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build());
    }

    private HttpResponse<String> send(HttpClient client, HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw error("network");
        } catch (IOException | IllegalArgumentException exception) {
            throw error("network");
        }
    }

    private static boolean isLoginPage(String html) {
        return Jsoup.parse(html).selectFirst("form#login-form, input[name=login-input-a]") != null;
    }

    private static void requireSuccess(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw error("response");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static WrmWmdException error(String key) {
        return new WrmWmdException("waterMeters.error." + key);
    }

}
