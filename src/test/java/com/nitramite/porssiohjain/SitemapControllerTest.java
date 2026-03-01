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

package com.nitramite.porssiohjain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SitemapControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Sitemap XML should return status 200 and contain URLs")
    void sitemapShouldReturnXml() throws Exception {
        mockMvc.perform(get("/sitemap.xml")
                        .header("Host", "porssiohjain.nitramite.com")
                        .secure(true))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(xpath("/urlset/url[1]/loc").string("http://porssiohjain.nitramite.com/"))
                .andExpect(xpath("/urlset/url[2]/loc").string("http://porssiohjain.nitramite.com/login"))
                .andExpect(xpath("/urlset/url[3]/loc").string("http://porssiohjain.nitramite.com/createAccount"))
                .andExpect(xpath("/urlset/url[4]/loc").string("http://porssiohjain.nitramite.com/device"))
                .andExpect(xpath("/urlset/url[5]/loc").string("http://porssiohjain.nitramite.com/controls"))
                .andExpect(xpath("/urlset/url[6]/loc").string("http://porssiohjain.nitramite.com/production-sources"))
                .andExpect(xpath("/urlset/url[1]/changefreq").string("weekly"))
                .andExpect(xpath("/urlset/url[1]/priority").string("0.5"));
    }
}
