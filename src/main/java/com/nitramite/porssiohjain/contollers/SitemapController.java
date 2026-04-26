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

package com.nitramite.porssiohjain.contollers;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
public class SitemapController {

    private static final List<String> pages = List.of(
            "/",
            "/login",
            "/createAccount",
            "/docs",
            "/docs/en/controls",
            "/docs/en/own-production",
            "/docs/en/weather-controls",
            "/docs/en/power-limits",
            "/docs/en/devices-and-sites",
            "/docs/en/api-documentation",
            "/docs/en/shelly-scripts",
            "/docs/fi/controls",
            "/docs/fi/own-production",
            "/docs/fi/weather-controls",
            "/docs/fi/power-limits",
            "/docs/fi/devices-and-sites",
            "/docs/fi/api-documentation",
            "/docs/fi/shelly-scripts"
            // "/device", // these require login
            // "/controls", // these require login
            // "/production-sources", // these require login
            // "/power-limits", // these require login
            // "/dashboard", // these require login
            // "/electricity-contracts", // these require login
            // "/sites", // these require login
            // "/settings", // these require login
            // "/resource-sharing" // these require login
    );

    private static final Map<String, List<String>> DOMAIN_PAGES = Map.of(
            "localhost:8080", pages,
            "porssiohjain.nitramite.com", pages,
            "app.porssiohjain.fi", pages,
            "app.energiaohjain.fi", pages
    );

    @GetMapping(value = "/sitemap_index.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemapIndex() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        String today = LocalDate.now().toString();
        for (String domain : DOMAIN_PAGES.keySet()) {
            sb.append("  <sitemap>\n");
            sb.append("    <loc>").append(domain).append("/sitemap.xml</loc>\n");
            sb.append("    <lastmod>").append(today).append("</lastmod>\n");
            sb.append("  </sitemap>\n");
        }

        sb.append("</sitemapindex>");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        return ResponseEntity.ok().headers(headers).body(sb.toString());
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap(HttpServletRequest request) {
        String host = request.getHeader("host");

        if (!DOMAIN_PAGES.containsKey(host)) {
            return ResponseEntity.badRequest().body("Unknown domain");
        }

        String canonicalHost = DOMAIN_PAGES.keySet().stream()
                .filter(host::equals)
                .findFirst()
                .orElse(host);
        List<String> pages = DOMAIN_PAGES.get(canonicalHost);
        String scheme = request.getScheme();
        String domain = scheme + "://" + canonicalHost;

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        String today = LocalDate.now().toString();
        for (String path : pages) {
            sb.append("  <url>\n");
            sb.append("    <loc>").append(escapeXml(domain)).append(escapeXml(path)).append("</loc>\n");
            sb.append("    <lastmod>").append(today).append("</lastmod>\n");
            sb.append("    <changefreq>weekly</changefreq>\n");
            sb.append("    <priority>0.5</priority>\n");
            sb.append("  </url>\n");
        }
        sb.append("</urlset>");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        return ResponseEntity.ok().headers(headers).body(sb.toString());
    }

    private static String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

}
