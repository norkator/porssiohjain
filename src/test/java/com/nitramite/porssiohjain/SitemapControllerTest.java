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

package com.nitramite.porssiohjain;

import com.nitramite.porssiohjain.mqtt.MqttService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

    @MockitoBean
    private MqttService mqttService;

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
                .andExpect(xpath("/urlset/url[4]/loc").string("http://porssiohjain.nitramite.com/docs"))
                .andExpect(xpath("/urlset/url[1]/changefreq").string("weekly"))
                .andExpect(xpath("/urlset/url[1]/priority").string("0.5"));
    }
}
