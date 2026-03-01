/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nitramite.porssiohjain.contollers;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
public class SitemapController {

    private static final Map<String, List<String>> DOMAIN_PAGES = Map.of(
            "https://porssiohjain.nitramite.com", List.of(
                    "/",
                    "/login",
                    "/createAccount",
                    "/device",
                    "/controls",
                    "/production-sources",
                    "/power-limits",
                    "/dashboard",
                    "/electricity-contracts",
                    "/sites",
                    "/settings",
                    "/resource-sharing"
            ),
            "https://porssiohjain.fi", List.of(
                    "/",
                    "/login",
                    "/createAccount",
                    "/device",
                    "/controls",
                    "/production-sources",
                    "/power-limits",
                    "/dashboard",
                    "/electricity-contracts",
                    "/sites",
                    "/settings",
                    "/resource-sharing"
            )
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
    public ResponseEntity<String> sitemap(@RequestParam(required = false) String domain) {
        if (domain == null || !DOMAIN_PAGES.containsKey(domain)) {
            return ResponseEntity.badRequest().body("Invalid or missing domain parameter");
        }

        List<String> pages = DOMAIN_PAGES.get(domain);
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        String today = LocalDate.now().toString();
        for (String path : pages) {
            sb.append("  <url>\n");
            sb.append("    <loc>").append(domain).append(path).append("</loc>\n");
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
}