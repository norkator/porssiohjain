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

import com.jayway.jsonpath.JsonPath;
import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.PowerLimitEntity;
import com.nitramite.porssiohjain.entity.ProductionSourceEntity;
import com.nitramite.porssiohjain.entity.SiteEntity;
import com.nitramite.porssiohjain.entity.enums.ProductionApiType;
import com.nitramite.porssiohjain.entity.enums.SiteType;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.PowerLimitRepository;
import com.nitramite.porssiohjain.entity.repository.ProductionSourceRepository;
import com.nitramite.porssiohjain.entity.repository.SiteRepository;
import com.nitramite.porssiohjain.entity.repository.TokenRepository;
import com.nitramite.porssiohjain.mqtt.MqttService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountStatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TokenRepository tokenRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private PowerLimitRepository powerLimitRepository;

    @Autowired
    private ProductionSourceRepository productionSourceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private MqttService mqttService;

    private AccountEntity testAccount;
    private String authToken;

    @BeforeEach
    void setUp() throws Exception {
        productionSourceRepository.deleteAll();
        powerLimitRepository.deleteAll();
        siteRepository.deleteAll();
        tokenRepository.deleteAll();
        accountRepository.deleteAll();

        AccountEntity account = AccountEntity.builder()
                .uuid(UUID.randomUUID())
                .secret(passwordEncoder.encode("supersecret"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        testAccount = accountRepository.save(account);

        String loginJson = String.format("""
                {
                    "uuid": "%s",
                    "secret": "supersecret"
                }
                """, testAccount.getUuid());

        String response = mockMvc.perform(post("/account/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        authToken = JsonPath.read(response, "$.token");
    }

    @Test
    @DisplayName("Should return account scoped power limit and production source stats")
    void getStatsShouldReturnAuthenticatedAccountStats() throws Exception {
        SiteEntity ownSite = siteRepository.save(SiteEntity.builder()
                .name("Own site")
                .type(SiteType.HOME)
                .enabled(true)
                .account(testAccount)
                .build());

        powerLimitRepository.save(PowerLimitEntity.builder()
                .account(testAccount)
                .site(ownSite)
                .timezone("Europe/Helsinki")
                .name("Main fuse")
                .limitKw(new BigDecimal("11.00"))
                .currentKw(new BigDecimal("4.25"))
                .enabled(true)
                .build());

        productionSourceRepository.save(ProductionSourceEntity.builder()
                .account(testAccount)
                .site(ownSite)
                .timezone("Europe/Helsinki")
                .name("Roof solar")
                .currentKw(new BigDecimal("2.10"))
                .peakKw(new BigDecimal("5.40"))
                .apiType(ProductionApiType.SHELLY)
                .enabled(true)
                .build());

        AccountEntity otherAccount = accountRepository.save(AccountEntity.builder()
                .uuid(UUID.randomUUID())
                .secret(passwordEncoder.encode("othersecret"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        SiteEntity otherSite = siteRepository.save(SiteEntity.builder()
                .name("Other site")
                .type(SiteType.OTHER)
                .enabled(true)
                .account(otherAccount)
                .build());

        powerLimitRepository.save(PowerLimitEntity.builder()
                .account(otherAccount)
                .site(otherSite)
                .timezone("Europe/Helsinki")
                .name("Should not leak")
                .limitKw(new BigDecimal("20.00"))
                .currentKw(new BigDecimal("9.99"))
                .enabled(true)
                .build());

        productionSourceRepository.save(ProductionSourceEntity.builder()
                .account(otherAccount)
                .site(otherSite)
                .timezone("Europe/Helsinki")
                .name("Should not leak")
                .currentKw(new BigDecimal("8.88"))
                .peakKw(new BigDecimal("12.34"))
                .apiType(ProductionApiType.SOFAR_SOLARMANPV)
                .enabled(true)
                .build());

        mockMvc.perform(get("/account/stats")
                        .header("Authorization", authToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.powerLimits.length()").value(1))
                .andExpect(jsonPath("$.powerLimits[0].name").value("Main fuse"))
                .andExpect(jsonPath("$.powerLimits[0].currentKw").value(4.25))
                .andExpect(jsonPath("$.productionSources.length()").value(1))
                .andExpect(jsonPath("$.productionSources[0].name").value("Roof solar"))
                .andExpect(jsonPath("$.productionSources[0].currentKw").value(2.10))
                .andExpect(jsonPath("$.productionSources[0].peakKw").value(5.40));
    }
}
