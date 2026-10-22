package com.nitramite.porssiohjain.services.wrmwmd;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class WrmWmdServiceTest {
    private HttpServer server;
    private WrmWmdService service;
    private final AtomicInteger logins = new AtomicInteger();
    private final AtomicInteger cards = new AtomicInteger();
    private boolean rejectLogin;
    private int cardStatus = 200;
    private String cardBody = card("140,8 m<sup>3</sup>");

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        service = new WrmWmdService(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
        server.createContext("/water-company", exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/login")) {
                logins.incrementAndGet();
                assertEquals("POST", exchange.getRequestMethod());
                assertEquals("sessionId=pre-login", exchange.getRequestHeaders().getFirst("Cookie"));
                Map<String, String> form = Arrays.stream(new String(exchange.getRequestBody().readAllBytes(),
                                StandardCharsets.UTF_8).split("&"))
                        .map(pair -> pair.split("=", 2)).collect(Collectors.toMap(pair -> decode(pair[0]), pair -> decode(pair[1])));
                assertEquals(Map.of("_csrf", "masked+csrf/token==", "mode", "water", "login-by", "ul",
                        "login-input-a", "00123+7", "login-input-b", "00001234"), form);
                if (rejectLogin) respond(exchange, 200, "<form id='login-form'>private-provider-details</form>");
                else {
                    exchange.getResponseHeaders().set("Set-Cookie", "sessionId=post-login; Path=/; HttpOnly");
                    exchange.getResponseHeaders().set("Location", "/water-company");
                    respond(exchange, 302, "");
                }
            } else {
                assertNull(exchange.getRequestHeaders().getFirst("Cookie"));
                exchange.getResponseHeaders().set("Set-Cookie", "sessionId=pre-login; Path=/; HttpOnly");
                respond(exchange, 200, "<meta name='csrf-token' content='masked+csrf/token=='><form id='login-form'></form>");
            }
        });
        server.createContext("/cards/watermeter-info", exchange -> {
            cards.incrementAndGet();
            String cookie = exchange.getRequestHeaders().getFirst("Cookie");
            if (!"sessionId=post-login".equals(cookie) && !"sessionId=browser-session".equals(cookie)) {
                respond(exchange, 401, "private-error");
            } else {
                exchange.getResponseHeaders().set("Location", "http://untrusted.invalid/credentials");
                respond(exchange, cardStatus, cardBody);
            }
        });
        server.start();
    }

    @AfterEach
    void cleanup() {
        server.stop(0);
    }

    @Test
    void logsInWithCsrfAndRotatedCookiesAndKeepsSeparateSessionsForEachPoll() {
        Instant before = Instant.now();
        var reading = service.readLatest(WrmWmdService.ORIGIN + "/water-company", "00123+7", "00001234");
        assertEquals(new BigDecimal("140.8"), reading.totalCubicMeters());
        assertFalse(reading.observedAt().isBefore(before));
        service.readLatest(WrmWmdService.ORIGIN + "/water-company", "00123+7", "00001234");
        assertEquals(2, logins.get());
        assertEquals(2, cards.get());
    }

    @Test
    void canReadUsingAnExistingSessionWithoutSubmittingCredentials() {
        assertEquals(new BigDecimal("140.8"), service.readWithSession("browser-session").totalCubicMeters());
        assertEquals(0, logins.get());
        assertEquals(1, cards.get());
    }

    @Test
    void rejectsInvalidLoginsWithoutExposingProviderContent() {
        rejectLogin = true;
        var error = assertThrows(WrmWmdException.class, () -> service.readLatest(
                WrmWmdService.ORIGIN + "/water-company", "00123+7", "00001234"));
        assertEquals("waterMeters.error.login", error.getMessage());
        assertNull(error.getCause());
        assertEquals(0, cards.get());
    }

    @Test
    void doesNotFollowRedirectsAndDetectsExpiredSessionEvenWhenLoginHtmlHasStatus200() {
        cardStatus = 302;
        assertEquals("waterMeters.error.login", assertThrows(WrmWmdException.class,
                () -> service.readWithSession("browser-session")).getMessage());
        cardStatus = 200;
        cardBody = "<form id='login-form'><input name='login-input-a'></form>";
        assertEquals("waterMeters.error.login", assertThrows(WrmWmdException.class,
                () -> service.readWithSession("browser-session")).getMessage());
    }

    @Test
    void parsesDecimalCommaAndSpaceGroupedTotalsAndZeroWithoutLosingPrecision() {
        assertEquals(new BigDecimal("1234.567"), WrmWmdService.parseCubicMeters(card("1&nbsp;234,567 m³")));
        assertEquals(new BigDecimal("0.0"), WrmWmdService.parseCubicMeters(card("0,0 m<sup>3</sup>")));
        assertEquals(new BigDecimal("140.8"), WrmWmdService.parseCubicMeters(card("140.8 m3")));
    }

    @Test
    void neverMistakesMissingTotalsForZeroOrChoosesBetweenMultipleMeters() {
        for (String html : new String[]{"", card("—"), card("-1 m3"), card("1,234,5 m3"),
                card("23 litres"), card("1 m3") + card("2 m3"), card("999999999999999999999 m3")}) {
            assertEquals("waterMeters.error.response", assertThrows(WrmWmdException.class,
                    () -> WrmWmdService.parseCubicMeters(html)).getMessage());
        }
    }

    @Test
    void validatesPortalAndCookieBeforeAnyCredentialsCanLeave() {
        for (String url : new String[]{"http://wmd.wrm-systems.fi/water-company", "https://evil.test/water-company",
                "https://wmd.wrm-systems.fi.evil.test/water-company", "https://user@wmd.wrm-systems.fi/water-company",
                "https://wmd.wrm-systems.fi:8443/water-company", "https://wmd.wrm-systems.fi/../cards",
                "https://wmd.wrm-systems.fi/water-company?secret=value"}) {
            assertEquals("waterMeters.error.portal", assertThrows(WrmWmdException.class,
                    () -> service.readLatest(url, "00123+7", "00001234")).getMessage());
        }
        assertThrows(WrmWmdException.class, () -> service.readWithSession("x\r\nHeader:secret"));
        assertEquals(0, cards.get());
        assertEquals(0, logins.get());
    }

    static String card(String value) {
        return "<div class='card'><div class='row'><div class='col'>Sarjanumero</div><div class='col'>00001234</div></div>"
                + "<div class='row'><div class='col'>\n Viimeisin lukema \n</div><div class='col'>" + value
                + "</div></div><div class='row'><div class='col'>Lähetysintervalli</div><div class='col'>Päivittäin</div></div></div>";
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
