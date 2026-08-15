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
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.ZigbeeDeviceMeasurementEntity;
import com.nitramite.porssiohjain.entity.enums.ZigbeeMeasurementType;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.ZigbeeDeviceMeasurementRepository;
import com.nitramite.porssiohjain.mqtt.MqttService;
import com.nitramite.porssiohjain.services.AccountService;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.PushNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MqttService mqttService;

    @MockitoBean
    private PushNotificationService pushNotificationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private ZigbeeDeviceMeasurementRepository zigbeeDeviceMeasurementRepository;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AuthService authService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("Should create account in real DB and return JSON")
    void createAccountShouldReturnJson() throws Exception {
        mockMvc.perform(post("/account/create")
                        .header("X-Forwarded-For", "10.10.10.10")
                        .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.uuid").isString())
                .andExpect(jsonPath("$.secret").isString())
                .andExpect(jsonPath("$.agreedTerms").value(true))
                .andExpect(jsonPath("$.agreedTermsAt").isString())
                .andExpect(jsonPath("$.createdAt").isString());

        assertThat(accountRepository.count()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should store hashed secret when creating account")
    void createAccountShouldHashStoredSecret() {
        AccountEntity created = accountService.createAccount("60.60.60.60", true);

        AccountEntity saved = accountRepository.findById(created.getId()).orElseThrow();

        assertThat(saved.getSecret()).isNotEqualTo(created.getSecret());
        assertThat(saved.getSecret()).startsWith("$2");
        assertThat(passwordEncoder.matches(created.getSecret(), saved.getSecret())).isTrue();
    }

    @Test
    @DisplayName("Should notify admins when creating account")
    void createAccountShouldNotifyAdmins() {
        AccountEntity created = accountService.createAccount("61.61.61.61", true);

        ArgumentCaptor<AccountEntity> accountCaptor = ArgumentCaptor.forClass(AccountEntity.class);
        verify(pushNotificationService).sendNewAccountCreatedAdminNotification(accountCaptor.capture(), eq(Locale.ENGLISH));
        assertThat(accountCaptor.getValue().getId()).isEqualTo(created.getId());
    }

    @Test
    @DisplayName("Should hit account creation rate limit after 2 attempts")
    void shouldReturnTooManyRequestsAfterRateLimit() throws Exception {
        mockMvc.perform(post("/account/create")
                        .header("X-Forwarded-For", "20.20.20.20")
                        .contentType("application/json"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/account/create")
                        .header("X-Forwarded-For", "20.20.20.20")
                        .contentType("application/json"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string("Too many account creations. Try again later."));
    }

    @Test
    @DisplayName("Should login successfully and return JSON token")
    void loginShouldReturnJson() throws Exception {
        String password = "supersecret";
        AccountEntity account = new AccountEntity();
        account.setUuid(UUID.randomUUID());
        account.setSecret(passwordEncoder.encode(password));
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(Instant.now());
        accountRepository.save(account);

        String requestBody = """
                {
                    "uuid": "%s",
                    "secret": "%s"
                }
                """.formatted(account.getUuid(), password);

        mockMvc.perform(post("/account/login")
                        .header("X-Forwarded-For", "30.30.30.30")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.expiresAt").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.refreshTokenExpiresAt").isString())
                .andExpect(jsonPath("$.accountId").value(account.getId()))
                .andExpect(jsonPath("$.locale").value("en"));
    }

    @Test
    @DisplayName("Should rotate refresh token and reject reuse")
    void refreshShouldRotateToken() throws Exception {
        String password = "refreshsecret";
        AccountEntity account = new AccountEntity();
        account.setUuid(UUID.randomUUID());
        account.setSecret(passwordEncoder.encode(password));
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(Instant.now());
        accountRepository.save(account);

        String loginResponse = mockMvc.perform(post("/account/login")
                        .header("X-Forwarded-For", "31.31.31.31")
                        .contentType("application/json")
                        .content("""
                                {"uuid":"%s","secret":"%s"}
                                """.formatted(account.getUuid(), password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String originalRefreshToken = objectMapper.readTree(loginResponse).get("refreshToken").asText();
        String refreshResponse = mockMvc.perform(post("/account/token/refresh")
                        .contentType("application/json")
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(originalRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(refreshResponse).get("refreshToken").asText())
                .isNotEqualTo(originalRefreshToken);

        mockMvc.perform(post("/account/token/refresh")
                        .contentType("application/json")
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(originalRefreshToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject blocked account login")
    void blockedAccountShouldNotLogin() throws Exception {
        String password = "supersecret";
        AccountEntity account = new AccountEntity();
        account.setUuid(UUID.randomUUID());
        account.setSecret(passwordEncoder.encode(password));
        account.setBlocked(true);
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(Instant.now());
        accountRepository.save(account);

        String requestBody = """
                {
                    "uuid": "%s",
                    "secret": "%s"
                }
                """.formatted(account.getUuid(), password);

        mockMvc.perform(post("/account/login")
                        .header("X-Forwarded-For", "35.35.35.35")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Account is blocked"));
    }

    @Test
    @DisplayName("Should not rate limit repeated successful logins")
    void shouldNotRateLimitSuccessfulLogins() throws Exception {
        String password = "supersecret";
        AccountEntity account = new AccountEntity();
        account.setUuid(UUID.randomUUID());
        account.setSecret(passwordEncoder.encode(password));
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(Instant.now());
        accountRepository.save(account);

        String requestBody = """
                {
                    "uuid": "%s",
                    "secret": "%s"
                }
                """.formatted(account.getUuid(), password);

        for (int i = 0; i < 11; i++) {
            mockMvc.perform(post("/account/login")
                            .header("X-Forwarded-For", "40.40.40.40")
                            .contentType("application/json")
                            .content(requestBody))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("Should hit login rate limit after failed attempts")
    void shouldReturnTooManyRequestsAfterFailedLoginLimit() throws Exception {
        String password = "supersecret";
        AccountEntity account = new AccountEntity();
        account.setUuid(UUID.randomUUID());
        account.setSecret(passwordEncoder.encode(password));
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(Instant.now());
        accountRepository.save(account);

        String requestBody = """
                {
                    "uuid": "%s",
                    "secret": "%s"
                }
                """.formatted(account.getUuid(), "wrongsecret");

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/account/login")
                            .header("X-Forwarded-For", "50.50.50.50")
                            .contentType("application/json")
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/account/login")
                        .header("X-Forwarded-For", "50.50.50.50")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string("Too many login attempts. Try again later."));
    }

    @Test
    @DisplayName("Should change account password when current password is correct")
    void shouldChangeAccountPassword() {
        String password = "Supersecret1";
        String newPassword = "Newsecret1";
        AccountEntity account = new AccountEntity();
        account.setUuid(UUID.randomUUID());
        account.setSecret(passwordEncoder.encode(password));
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(Instant.now());
        accountRepository.save(account);

        boolean changed = accountService.changeSecret(account.getId(), password, newPassword);

        AccountEntity saved = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(changed).isTrue();
        assertThat(passwordEncoder.matches(newPassword, saved.getSecret())).isTrue();
        assertThat(passwordEncoder.matches(password, saved.getSecret())).isFalse();
    }

    @Test
    @DisplayName("Should reject account password that does not meet requirements")
    void shouldRejectInvalidAccountPassword() {
        assertThat(AccountService.isValidSecret("short1A")).isFalse();
        assertThat(AccountService.isValidSecret("lowercase1")).isFalse();
        assertThat(AccountService.isValidSecret("NoNumbers")).isFalse();
        assertThat(AccountService.isValidSecret("Validpass1")).isTrue();
    }

    @Test
    @DisplayName("Should download authenticated account data export")
    void shouldDownloadAccountDataExport() throws Exception {
        AccountEntity created = accountService.createAccount("70.70.70.70", true);
        AccountEntity other = accountService.createAccount("70.70.70.72", true);
        DeviceEntity ownDevice = deviceRepository.save(exportTestDevice(created, "Export own device"));
        DeviceEntity otherDevice = deviceRepository.save(exportTestDevice(other, "Export other device"));
        zigbeeDeviceMeasurementRepository.save(exportTestMeasurement(created, ownDevice));
        zigbeeDeviceMeasurementRepository.save(exportTestMeasurement(other, otherDevice));
        String token = authService.login("70.70.70.71", created.getUuid(), created.getSecret()).getToken();

        byte[] content = mockMvc.perform(get("/me/export")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/zip"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("-export.zip")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        JsonNode export = objectMapper.readTree(readZipEntry(content, "account-data.json"));
        assertThat(export.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(export.get("accountId").asLong()).isEqualTo(created.getId());

        JsonNode accountRows = export.get("tables").findValues("entity").stream()
                .filter(node -> node.asText().equals("AccountEntity"))
                .findFirst()
                .orElseThrow();
        assertThat(accountRows.asText()).isEqualTo("AccountEntity");

        JsonNode deviceTable = findTable(export, "DeviceEntity");
        assertThat(deviceTable.get("rows").findValues("id").stream().map(JsonNode::asLong).toList())
                .contains(ownDevice.getId())
                .doesNotContain(otherDevice.getId());

        assertThat(hasTable(export, "ZigbeeDeviceMeasurementEntity")).isFalse();
        JsonNode measurementSummary = findOmittedHighVolumeTable(export, "ZigbeeDeviceMeasurementEntity");
        assertThat(measurementSummary.get("rowCount").asLong()).isEqualTo(1L);
        assertThat(measurementSummary.get("reason").asText()).contains("High-volume");
    }

    private DeviceEntity exportTestDevice(AccountEntity account, String name) {
        return DeviceEntity.builder()
                .account(account)
                .deviceName(name)
                .timezone("Europe/Helsinki")
                .apiOnline(false)
                .mqttOnline(false)
                .build();
    }

    private ZigbeeDeviceMeasurementEntity exportTestMeasurement(AccountEntity account, DeviceEntity device) {
        Instant measuredAt = Instant.parse("2026-08-15T10:00:00Z");
        return ZigbeeDeviceMeasurementEntity.builder()
                .account(account)
                .device(device)
                .gatewayId(UUID.randomUUID())
                .zigbeeIeee(UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .profile("temperature-sensor")
                .measurementType(ZigbeeMeasurementType.TEMPERATURE)
                .measurementKey("temperature")
                .value(BigDecimal.valueOf(21.5))
                .measuredAt(measuredAt)
                .receivedAt(measuredAt)
                .build();
    }

    private JsonNode findTable(JsonNode export, String entityName) {
        for (JsonNode table : export.get("tables")) {
            if (entityName.equals(table.get("entity").asText())) {
                return table;
            }
        }
        throw new AssertionError("Missing export table " + entityName);
    }

    private boolean hasTable(JsonNode export, String entityName) {
        for (JsonNode table : export.get("tables")) {
            if (entityName.equals(table.get("entity").asText())) {
                return true;
            }
        }
        return false;
    }

    private JsonNode findOmittedHighVolumeTable(JsonNode export, String entityName) {
        for (JsonNode table : export.get("omittedHighVolumeTables")) {
            if (entityName.equals(table.get("entity").asText())) {
                return table;
            }
        }
        throw new AssertionError("Missing high-volume export summary " + entityName);
    }

    private byte[] readZipEntry(byte[] zipBytes, String entryName) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            var entry = zip.getNextEntry();
            while (entry != null) {
                if (entryName.equals(entry.getName())) {
                    return zip.readAllBytes();
                }
                entry = zip.getNextEntry();
            }
        }
        throw new AssertionError("Missing ZIP entry " + entryName);
    }

}
