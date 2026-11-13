package com.nitramite.porssiohjain.services.watermeters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nitramite.porssiohjain.entity.enums.WaterMeterProvider;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
public class TampereenVesiService implements WaterMeterClient {
    public static final String PORTAL = "https://asiointi.tampereenvesi.fi/eServices/Online/IndexNoAuth";
    private static final URI ORIGIN = URI.create("https://asiointi.tampereenvesi.fi");
    private static final Duration TIMEOUT = Duration.ofSeconds(25);
    private final ObjectMapper json = new ObjectMapper();

    @Override public WaterMeterProvider provider() { return WaterMeterProvider.TAMPEREEN_VESI; }

    @Override public String normalizePortalUrl(String value) {
        if (!PORTAL.equals(value == null ? "" : value.strip())) throw error("portal");
        return PORTAL;
    }

    @Override public Reading readLatest(String portal, String username, String password) {
        throw error("baseline");
    }

    @Override public Map<LocalDate, BigDecimal> readDaily(String portal, String username, String password,
                                                       String point, LocalDate start) {
        normalizePortalUrl(portal);
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)).build();
            HttpResponse<String> page = get(client, URI.create(PORTAL));
            String token = token(page.body());
            HttpResponse<String> login = post(client, "/eServices/Online/Login", Map.of(
                    "UserName", username, "Password", password, "__RequestVerificationToken", token));
            // Follow only same-origin redirects; credentials are posted exactly once to the fixed login endpoint.
            for (int count = 0; login.statusCode() >= 300 && login.statusCode() < 400 && count < 5; count++) {
                URI next = login.uri().resolve(login.headers().firstValue("Location").orElseThrow(() -> error("login")));
                if (!"https".equals(next.getScheme()) || !ORIGIN.getHost().equals(next.getHost())
                        || next.getPort() != -1 || next.getUserInfo() != null) throw error("login");
                login = get(client, next);
            }
            if (login.statusCode() != 200 || Jsoup.parse(login.body()).selectFirst("input[name=Password]") != null)
                throw error("login");
            token = token(login.body());
            HttpResponse<String> response = post(client, "/Reporting/SessionlessConsumption/GetMpConsumptionModel",
                    Map.of("mpCode", point, "mpSourceCompanyCode", "", "startDate", start.toString(),
                            "__RequestVerificationToken", token));
            if (response.statusCode() == 401 || response.statusCode() == 403 || response.statusCode() == 302)
                throw error("login");
            if (response.statusCode() != 200) throw error("response");
            return parseDaily(json.readTree(response.body()), point);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw error("network");
        } catch (java.io.IOException | IllegalArgumentException e) {
            throw error("network");
        }
    }

    static Map<LocalDate, BigDecimal> parseDaily(JsonNode root, String point) {
        if (!point.equals(root.path("MeteringPointCode").asText()) || !root.path("Data").path("IsValid").asBoolean())
            throw error("response");
        JsonNode series = root.path("Data").path("Days").path("Consumption");
        if (!"Day".equals(series.path("Resolution").asText()) || !"m³".equals(series.path("Unit").asText())
                || !series.path("Data").isArray()) throw error("response");
        Map<LocalDate, BigDecimal> result = new TreeMap<>();
        for (JsonNode pair : series.path("Data")) {
            if (!pair.isArray() || pair.size() != 2 || !pair.get(0).isIntegralNumber()) throw error("response");
            if (pair.get(1).isNull()) continue;
            if (!pair.get(1).isNumber()) throw error("response");
            LocalDate date = Instant.ofEpochMilli(pair.get(0).longValue()).atZone(ZoneId.of("Europe/Helsinki")).toLocalDate();
            BigDecimal value = pair.get(1).decimalValue().setScale(6, java.math.RoundingMode.HALF_UP);
            if (value.signum() < 0 || value.precision() - value.scale() > 14 || result.put(date, value) != null)
                throw error("response");
        }
        return result;
    }

    private static String token(String html) {
        var element = Jsoup.parse(html).selectFirst("input[name=__RequestVerificationToken]");
        if (element == null || element.val().isBlank()) throw error("login");
        return element.val();
    }
    private static HttpResponse<String> get(HttpClient client, URI uri) throws java.io.IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<String> post(HttpClient client, String path, Map<String, String> fields)
            throws java.io.IOException, InterruptedException {
        StringJoiner form = new StringJoiner("&");
        fields.forEach((k, v) -> form.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(v, StandardCharsets.UTF_8)));
        return client.send(HttpRequest.newBuilder(ORIGIN.resolve(path)).timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Origin", ORIGIN.toString()).header("Referer", PORTAL)
                .header("X-Requested-With", "XMLHttpRequest")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString())).build(), HttpResponse.BodyHandlers.ofString());
    }
    private static WaterMeterException error(String key) { return new WaterMeterException("waterMeters.error." + key); }
}
