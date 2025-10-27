package com.nitramite.porssiohjain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(xpath("/urlset/url[1]/loc").string("https://porssiohjain.nitramite.com/"))
                .andExpect(xpath("/urlset/url[2]/loc").string("https://porssiohjain.nitramite.com/login"))
                .andExpect(xpath("/urlset/url[3]/loc").string("https://porssiohjain.nitramite.com/createAccount"))
                .andExpect(xpath("/urlset/url[4]/loc").string("https://porssiohjain.nitramite.com/device"))
                .andExpect(xpath("/urlset/url[5]/loc").string("https://porssiohjain.nitramite.com/controls"))
                .andExpect(xpath("/urlset/url[6]/loc").string("https://porssiohjain.nitramite.com/dashboard"))
                .andExpect(xpath("/urlset/url[1]/changefreq").string("weekly"))
                .andExpect(xpath("/urlset/url[1]/priority").string("0.5"));
    }
}
