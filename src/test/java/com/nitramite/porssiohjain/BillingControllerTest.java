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

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.enums.AccountTier;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.mqtt.MqttService;
import com.nitramite.porssiohjain.services.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BillingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MqttService mqttService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthService authService;

    @Test
    @DisplayName("Should expose Google Play subscription products")
    void shouldExposeSubscriptionProducts() throws Exception {
        String token = createAuthToken();

        mockMvc.perform(get("/billing/products")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.productId == 'porssiohjain_pro_monthly')]").exists())
                .andExpect(jsonPath("$[?(@.productId == 'porssiohjain_business_monthly')]").exists());
    }

    @Test
    @DisplayName("Should activate Pro tier after verified Google Play purchase")
    void shouldActivateProTierAfterGooglePlayPurchase() throws Exception {
        AccountEntity account = createAccount();
        String token = authService.createTokenForAccount(account).getToken();

        mockMvc.perform(post("/billing/google-play/purchases")
                        .header("Authorization", token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "productId": "porssiohjain_pro_monthly",
                                  "purchaseToken": "test-pro-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("PRO"))
                .andExpect(jsonPath("$.subscriptionStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.productId").value("porssiohjain_pro_monthly"))
                .andExpect(jsonPath("$.expiresAt").isString());

        AccountEntity saved = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(saved.getTier()).isEqualTo(AccountTier.PRO);
    }

    @Test
    @DisplayName("Should reject unsupported subscription product")
    void shouldRejectUnsupportedSubscriptionProduct() throws Exception {
        String token = createAuthToken();

        mockMvc.perform(post("/billing/google-play/purchases")
                        .header("Authorization", token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "productId": "unknown_monthly",
                                  "purchaseToken": "test-token"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported subscription product: unknown_monthly"));
    }

    private String createAuthToken() {
        return authService.createTokenForAccount(createAccount()).getToken();
    }

    private AccountEntity createAccount() {
        AccountEntity account = new AccountEntity();
        account.setUuid(UUID.randomUUID());
        account.setSecret(passwordEncoder.encode("Supersecret1"));
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(Instant.now());
        return accountRepository.save(account);
    }
}
