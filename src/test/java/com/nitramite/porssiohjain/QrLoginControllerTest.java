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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.mqtt.MqttService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QrLoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MqttService mqttService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Should create QR login challenge")
    void shouldCreateQrLoginChallenge() throws Exception {
        String response = mockMvc.perform(post("/account/qr-login/challenges")
                        .header("X-Forwarded-For", "70.70.70.70")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.challengeId").isString())
                .andExpect(jsonPath("$.browserSecret").isString())
                .andExpect(jsonPath("$.qrPayload").isString())
                .andExpect(jsonPath("$.expiresAt").isString())
                .andExpect(jsonPath("$.pollIntervalMs").value(1500))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        URI qrUri = URI.create(json.get("qrPayload").asText());

        assertThat(qrUri.getScheme()).isEqualTo("porssiohjain");
        assertThat(qrUri.getHost()).isEqualTo("qr-login");
        assertThat(qrUri.getQuery()).contains("challengeId=");
        assertThat(qrUri.getQuery()).contains("scanSecret=");
    }

    @Test
    @DisplayName("Should approve and complete QR login challenge once")
    void shouldApproveAndCompleteQrLoginChallengeOnce() throws Exception {
        AccountEntity account = createAccount("QrSecret123");
        String authToken = login(account.getUuid(), "QrSecret123", "71.71.71.71");
        JsonNode challenge = createChallenge("72.72.72.72");
        String challengeId = challenge.get("challengeId").asText();
        String browserSecret = challenge.get("browserSecret").asText();
        String scanSecret = extractQueryParam(challenge.get("qrPayload").asText(), "scanSecret");

        mockMvc.perform(post("/account/qr-login/challenges/" + challengeId + "/complete")
                        .contentType("application/json")
                        .content("""
                                {
                                    "browserSecret": "%s"
                                }
                                """.formatted(browserSecret)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(post("/account/qr-login/challenges/" + challengeId + "/approve")
                        .header("Authorization", authToken)
                        .contentType("application/json")
                        .content("""
                                {
                                    "scanSecret": "%s"
                                }
                                """.formatted(scanSecret)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(post("/account/qr-login/challenges/" + challengeId + "/complete")
                        .contentType("application/json")
                        .content("""
                                {
                                    "browserSecret": "%s"
                                }
                                """.formatted(browserSecret)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.accountId").value(account.getId()))
                .andExpect(jsonPath("$.locale").value("en"));

        mockMvc.perform(post("/account/qr-login/challenges/" + challengeId + "/complete")
                        .contentType("application/json")
                        .content("""
                                {
                                    "browserSecret": "%s"
                                }
                                """.formatted(browserSecret)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Should reject QR approval with wrong scan secret")
    void shouldRejectWrongScanSecret() throws Exception {
        AccountEntity account = createAccount("QrSecret456");
        String authToken = login(account.getUuid(), "QrSecret456", "73.73.73.73");
        JsonNode challenge = createChallenge("74.74.74.74");
        String challengeId = challenge.get("challengeId").asText();

        mockMvc.perform(post("/account/qr-login/challenges/" + challengeId + "/approve")
                        .header("Authorization", authToken)
                        .contentType("application/json")
                        .content("""
                                {
                                    "scanSecret": "wrong"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private AccountEntity createAccount(String password) {
        AccountEntity account = new AccountEntity();
        account.setUuid(UUID.randomUUID());
        account.setSecret(passwordEncoder.encode(password));
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(Instant.now());
        return accountRepository.save(account);
    }

    private String login(UUID uuid, String password, String ip) throws Exception {
        String response = mockMvc.perform(post("/account/login")
                        .header("X-Forwarded-For", ip)
                        .contentType("application/json")
                        .content("""
                                {
                                    "uuid": "%s",
                                    "secret": "%s"
                                }
                                """.formatted(uuid, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("token").asText();
    }

    private JsonNode createChallenge(String ip) throws Exception {
        String response = mockMvc.perform(post("/account/qr-login/challenges")
                        .header("X-Forwarded-For", ip)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response);
    }

    private String extractQueryParam(String uriValue, String name) {
        String query = URI.create(uriValue).getQuery();
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && parts[0].equals(name)) {
                return parts[1];
            }
        }

        throw new IllegalArgumentException("Missing query parameter " + name);
    }
}
