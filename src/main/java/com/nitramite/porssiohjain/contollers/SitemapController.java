package com.nitramite.porssiohjain.contollers;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.util.List;

@Controller
public class SitemapController {

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        List<String> urls = List.of(
                "https://porssiohjain.nitramite.com/",
                "https://porssiohjain.nitramite.com/login",
                "https://porssiohjain.nitramite.com/createAccount",
                "https://porssiohjain.nitramite.com/device",
                "https://porssiohjain.nitramite.com/controls",
                "https://porssiohjain.nitramite.com/dashboard"
        );

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        String today = LocalDate.now().toString();
        for (String url : urls) {
            sb.append("  <url>\n");
            sb.append("    <loc>").append(url).append("</loc>\n");
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