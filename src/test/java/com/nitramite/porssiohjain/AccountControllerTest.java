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
import com.nitramite.porssiohjain.entity.SiteEntity;
import com.nitramite.porssiohjain.entity.ZigbeeDeviceMeasurementEntity;
import com.nitramite.porssiohjain.entity.enums.AccountActivitySource;
import com.nitramite.porssiohjain.entity.enums.SiteType;
import com.nitramite.porssiohjain.entity.enums.ZigbeeMeasurementType;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.SiteRepository;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
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
    private SiteRepository siteRepository;

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
                .andExpect(jsonPath("$.locale").value("en"))
                .andExpect(jsonPath("$.agreedTerms").value(true))
                .andExpect(jsonPath("$.agreedTermsAt").isString())
                .andExpect(jsonPath("$.createdAt").isString());

        assertThat(accountRepository.count()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should persist requested locale when creating account")
    void createAccountShouldPersistRequestedLocale() throws Exception {
        String responseBody = mockMvc.perform(post("/account/create")
                        .header("X-Forwarded-For", "10.10.10.11")
                        .contentType("application/json")
                        .content("""
                                {"locale":"fi-FI"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.locale").value("fi"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID accountUuid = UUID.fromString(objectMapper.readTree(responseBody).get("uuid").asText());
        AccountEntity saved = accountRepository.findByUuid(accountUuid).orElseThrow();
        assertThat(saved.getLocale()).isEqualTo("fi");
    }

    @Test
    @DisplayName("Should persist activity source when creating account")
    void createAccountShouldPersistActivitySource() throws Exception {
        String responseBody = mockMvc.perform(post("/account/create")
                        .header("X-Forwarded-For", "10.10.10.12")
                        .contentType("application/json")
                        .content("""
                                {"activitySource":"HYBRID_WEB"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID accountUuid = UUID.fromString(objectMapper.readTree(responseBody).get("uuid").asText());
        AccountEntity saved = accountRepository.findByUuid(accountUuid).orElseThrow();
        assertThat(saved.getLastActivitySource()).isEqualTo(AccountActivitySource.HYBRID_WEB);
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
    @DisplayName("Should persist activity source when logging in")
    void loginShouldPersistActivitySource() throws Exception {
        String password = "activitysecret";
        AccountEntity account = new AccountEntity();
        account.setUuid(UUID.randomUUID());
        account.setSecret(passwordEncoder.encode(password));
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(Instant.now());
        accountRepository.save(account);

        mockMvc.perform(post("/account/login")
                        .header("X-Forwarded-For", "30.30.30.31")
                        .contentType("application/json")
                        .content("""
                                {
                                    "uuid": "%s",
                                    "secret": "%s",
                                    "activitySource": "HYBRID_ANDROID"
                                }
                                """.formatted(account.getUuid(), password)))
                .andExpect(status().isOk());

        AccountEntity saved = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(saved.getLastActivitySource()).isEqualTo(AccountActivitySource.HYBRID_ANDROID);
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
        String deviceName = "Oma laite: \"Lämmitys\"\nSecond line";
        DeviceEntity ownDevice = deviceRepository.save(exportTestDevice(created, deviceName));
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

        Map<String, byte[]> files = readZipEntries(content);
        assertThat(files).containsOnlyKeys("README.txt", "data.jsonl");
        Map<String, List<JsonNode>> records = readExportRecords(files.get("data.jsonl"));
        assertThat(records).containsKeys("account", "device", "history-summary");
        assertThat(records).doesNotContainKey("zigbee-device-measurement");
        assertThat(records.keySet()).noneMatch(name -> name.contains("Entity"));

        List<JsonNode> accounts = records.get("account");
        assertThat(accounts).hasSize(1);
        assertThat(accounts.getFirst().get("id").asLong()).isEqualTo(created.getId());
        assertThat(accounts.getFirst().get("uuid").asText()).isEqualTo(created.getUuid().toString());

        List<JsonNode> devices = records.get("device");
        assertThat(devices).hasSize(1);
        JsonNode device = devices.getFirst();
        assertThat(device.get("id").asLong()).isEqualTo(ownDevice.getId()).isNotEqualTo(otherDevice.getId());
        assertThat(device.get("account").isIntegralNumber()).isTrue();
        assertThat(device.get("account").asLong()).isEqualTo(created.getId());
        assertThat(device.get("deviceName").asText()).isEqualTo(deviceName);
        assertThat(device.get("lastTelemetry").isNull()).isTrue();
        assertThat(device.get("apiOnline").asBoolean()).isFalse();
        assertThat(new String(files.get("data.jsonl"), StandardCharsets.UTF_8))
                .doesNotContain("\n ", "\"entity\":", "\"rows\":", "DeviceEntity", "AccountEntity");

        List<JsonNode> summaries = records.get("history-summary");
        assertThat(summaries).hasSize(1);
        JsonNode measurementSummary = summaries.getFirst();
        assertThat(measurementSummary.get("data").asText()).isEqualTo("zigbee-device-measurement");
        assertThat(measurementSummary.get("rowCount").asLong()).isEqualTo(1L);
        JsonNode measuredAt = objectMapper.readTree(objectMapper.writeValueAsBytes(Instant.parse("2026-08-15T10:00:00Z")));
        assertThat(measurementSummary.get("earliest")).isEqualTo(measuredAt);
        assertThat(measurementSummary.get("latest")).isEqualTo(measuredAt);
    }

    @Test
    @DisplayName("Should export every record across data batches and omit empty history summaries")
    void shouldExportAllDataBatches() throws Exception {
        AccountEntity created = accountService.createAccount("70.70.70.73", true);
        siteRepository.save(SiteEntity.builder().account(created).name("Export site").type(SiteType.HOME).build());
        List<DeviceEntity> devices = deviceRepository.saveAll(IntStream.range(0, 501)
                .mapToObj(index -> exportTestDevice(created, "Export batch device " + index))
                .toList());
        String token = authService.login("70.70.70.74", created.getUuid(), created.getSecret()).getToken();

        byte[] content = mockMvc.perform(get("/me/export").header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        Map<String, byte[]> files = readZipEntries(content);
        Map<String, List<JsonNode>> records = readExportRecords(files.get("data.jsonl"));
        List<JsonNode> exportedDevices = records.get("device");
        assertThat(exportedDevices.stream().map(row -> row.get("id").asLong()).toList())
                .containsExactlyInAnyOrderElementsOf(devices.stream().map(DeviceEntity::getId).toList());
        assertThat(records).doesNotContainKey("history-summary");

        List<JsonNode> sites = records.get("site");
        assertThat(sites).hasSize(1);
        for (JsonNode site : sites) {
            assertThat(site.has("controls")).isFalse();
            assertThat(site.has("powerLimits")).isFalse();
            assertThat(site.has("productionSources")).isFalse();
        }
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

    private Map<String, List<JsonNode>> readExportRecords(byte[] content) throws IOException {
        Map<String, List<JsonNode>> records = new LinkedHashMap<>();
        for (String line : new String(content, StandardCharsets.UTF_8).lines().toList()) {
            JsonNode record = objectMapper.readTree(line);
            assertThat(record.isObject()).isTrue();
            assertThat(record.size()).isEqualTo(1);
            var entry = record.fields().next();
            records.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).add(entry.getValue());
        }
        return records;
    }

    private Map<String, byte[]> readZipEntries(byte[] zipBytes) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            var entry = zip.getNextEntry();
            while (entry != null) {
                files.put(entry.getName(), zip.readAllBytes());
                entry = zip.getNextEntry();
            }
        }
        return files;
    }

}
