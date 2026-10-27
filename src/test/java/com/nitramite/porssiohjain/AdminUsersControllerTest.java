package com.nitramite.porssiohjain;

import com.nitramite.porssiohjain.auth.AuthContext;
import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.SiteEntity;
import com.nitramite.porssiohjain.entity.TokenEntity;
import com.nitramite.porssiohjain.entity.enums.SiteType;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.SiteRepository;
import com.nitramite.porssiohjain.entity.repository.TokenRepository;
import com.nitramite.porssiohjain.mqtt.MqttService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.test.context.transaction.TestTransaction;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminUsersControllerTest {
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired DeviceRepository devices;
    @Autowired SiteRepository sites;
    @Autowired TokenRepository tokens;
    @Autowired AuthContext context;
    @MockitoBean MqttService mqttService;

    private AccountEntity admin;
    private AccountEntity user;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setup() {
        admin = account(true, "admin@example.test");
        user = account(false, "target@example.test");
        adminToken = token(admin);
        userToken = token(user);
    }

    @Test
    void listingRequiresAdminAndDoesNotExposeSecrets() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users").header("Authorization", userToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").header("Authorization", adminToken).param("search", "TARGET@"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.users[0].id").value(user.getId()))
                .andExpect(jsonPath("$.users[0].secret").doesNotExist());
        mvc.perform(get("/api/admin/users").header("Authorization", adminToken).param("search", user.getUuid().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/admin/users").header("Authorization", adminToken).param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listingOrdersAccountsByIdAndIncludesOwnedDeviceCount() throws Exception {
        String emailDomain = UUID.randomUUID() + ".example.test";
        admin.setEmail("admin@" + emailDomain);
        user.setEmail("target@" + emailDomain);
        accounts.saveAndFlush(admin);
        accounts.saveAndFlush(user);
        devices.saveAndFlush(DeviceEntity.builder().account(user).deviceName("Owned device")
                .timezone("Europe/Helsinki").build());

        mvc.perform(get("/api/admin/users").header("Authorization", adminToken).param("search", emailDomain))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.users[0].id").value(admin.getId()))
                .andExpect(jsonPath("$.users[0].deviceCount").value(0))
                .andExpect(jsonPath("$.users[1].id").value(user.getId()))
                .andExpect(jsonPath("$.users[1].deviceCount").value(1));
    }

    @Test
    void previewUsesTargetAccountAndReturnsToAdminWithoutChangingToken() throws Exception {
        mvc.perform(post("/api/admin/users/{id}/preview", user.getId()).header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(user.getId()));
        mvc.perform(get("/me").header("Authorization", adminToken).header("X-View-As-Account", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(user.getId()))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.admin").value(false))
                .andExpect(jsonPath("$.impersonating").value(true));
        assertNull(context.getAccountId());
        mvc.perform(get("/account/stats").header("Authorization", adminToken).header("X-View-As-Account", user.getId()))
                .andExpect(status().isOk());
        mvc.perform(get("/me").header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountId").value(admin.getId()))
                .andExpect(jsonPath("$.admin").value(true)).andExpect(jsonPath("$.impersonating").value(false));
    }

    @Test
    void forgedOrInvalidPreviewCannotReadAnotherAccount() throws Exception {
        mvc.perform(get("/me").header("Authorization", userToken).header("X-View-As-Account", admin.getId()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/me").header("Authorization", adminToken).header("X-View-As-Account", admin.getId()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/me").header("Authorization", adminToken).header("X-View-As-Account", "invalid"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/me").header("Authorization", adminToken).header("X-View-As-Account", Long.MAX_VALUE))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/users/{id}/preview", admin.getId()).header("Authorization", adminToken))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/users/{id}/preview", user.getId()).header("Authorization", userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void supportStillBlocksCredentialsExportAccountDeletionPurchasesAndAdminAccess() throws Exception {
        for (var request : java.util.List.of(delete("/me"), post("/me/password"),
                get("/me/export"), get("/api/admin/users"), post("/api/admin/users/{id}/preview", user.getId()),
                post("/billing/google-play/purchases"), post("/account/qr-login/challenges/{id}/approve", UUID.randomUUID()))) {
            mvc.perform(request.header("Authorization", adminToken).header("X-View-As-Account", user.getId()))
                    .andExpect(status().isForbidden());
            assertNull(context.getAccountId());
        }
    }

    @Test
    void supportCanCreateAndEditSitesUsingTheSelectedAccountsQuota() throws Exception {
        String siteRequest = """
                {"name":"Home","type":"HOME","enabled":false,"timezone":"Europe/Helsinki"}
                """;
        mvc.perform(post("/api/sites").header("Authorization", adminToken).header("X-View-As-Account", user.getId())
                        .contentType("application/json").content(siteRequest))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Home"));
        var site = sites.findByAccountId(user.getId()).getFirst();
        assertTrue(sites.findByAccountId(admin.getId()).isEmpty());
        mvc.perform(put("/api/sites/{id}", site.getId()).header("Authorization", adminToken)
                        .header("X-View-As-Account", user.getId()).contentType("application/json")
                        .content(siteRequest.replace("Home", "Support edit")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Support edit"));
        mvc.perform(post("/api/sites").header("Authorization", adminToken).header("X-View-As-Account", user.getId())
                        .contentType("application/json").content(siteRequest))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Site limit reached for this account (1)."));
        assertEquals(1, sites.countByAccountId(user.getId()));
        var other = sites.saveAndFlush(SiteEntity.builder().account(admin).name("Admin home").type(SiteType.HOME).build());
        mvc.perform(put("/api/sites/{id}", other.getId()).header("Authorization", adminToken)
                        .header("X-View-As-Account", user.getId()).contentType("application/json").content(siteRequest))
                .andExpect(status().isBadRequest());
        assertEquals("Admin home", sites.findById(other.getId()).orElseThrow().getName());
        assertNull(context.getAccountId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void supportCanCommandAndDeleteOnlyTheSelectedUsersDevices() throws Exception {
        var targetDevice = devices.saveAndFlush(DeviceEntity.builder().account(user).deviceName("Target relay")
                .timezone("Europe/Helsinki").mqttOnline(true).build());
        var otherDevice = devices.saveAndFlush(DeviceEntity.builder().account(admin).deviceName("Admin relay")
                .timezone("Europe/Helsinki").mqttOnline(true).build());
        mvc.perform(post("/devices/{id}/mqtt-relays/0", targetDevice.getId()).header("Authorization", adminToken)
                        .header("X-View-As-Account", user.getId()).contentType("application/json").content("{\"on\":true}"))
                .andExpect(status().isOk());
        verify(mqttService).switchControl(targetDevice.getUuid().toString(), 0, true, targetDevice.getDevicePlatform());
        mvc.perform(post("/devices/{id}/mqtt-relays/0", otherDevice.getId()).header("Authorization", adminToken)
                        .header("X-View-As-Account", user.getId()).contentType("application/json").content("{\"on\":true}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/devices/{id}", otherDevice.getId()).header("Authorization", adminToken)
                        .header("X-View-As-Account", user.getId())).andExpect(status().isBadRequest());
        mvc.perform(delete("/devices/{id}", targetDevice.getId()).header("Authorization", adminToken)
                        .header("X-View-As-Account", user.getId())).andExpect(status().isNoContent());
        assertFalse(devices.existsById(targetDevice.getId()));
        assertTrue(devices.existsById(otherDevice.getId()));
        assertNull(context.getAccountId());
    }

    @AfterEach
    void cleanCommittedCommandFixtures() {
        if (TestTransaction.isActive()) return;
        devices.deleteAll(devices.findByAccountId(admin.getId()));
        devices.deleteAll(devices.findByAccountId(user.getId()));
        tokens.findByToken(adminToken).ifPresent(tokens::delete);
        tokens.findByToken(userToken).ifPresent(tokens::delete);
        accounts.deleteAllById(java.util.List.of(admin.getId(), user.getId()));
    }

    @Test
    void supportSettingsUpdateChangesOnlyTheSelectedUserAndRetainsTheAdminSession() throws Exception {
        mvc.perform(put("/me").header("Authorization", adminToken).header("X-View-As-Account", user.getId())
                        .contentType("application/json").content("{\"email\":\"support@example.test\",\"locale\":\"fi\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountId").value(user.getId()))
                .andExpect(jsonPath("$.email").value("support@example.test"))
                .andExpect(jsonPath("$.admin").value(false)).andExpect(jsonPath("$.impersonating").value(true));
        mvc.perform(get("/me").header("Authorization", adminToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(admin.getId()))
                .andExpect(jsonPath("$.email").value("admin@example.test"))
                .andExpect(jsonPath("$.admin").value(true));
        assertEquals(admin.getId(), tokens.findByTokenWithAccount(adminToken).orElseThrow().getAccount().getId());
        assertNull(context.getAccountId());
    }

    @Test
    void demoTargetAndForgedSupportHeaderStillCannotWrite() throws Exception {
        var targetDevice = devices.saveAndFlush(DeviceEntity.builder().account(user).deviceName("Target relay")
                .timezone("Europe/Helsinki").mqttOnline(true).build());
        mvc.perform(delete("/devices/{id}", targetDevice.getId()).header("Authorization", userToken)
                        .header("X-View-As-Account", admin.getId())).andExpect(status().isForbidden());
        user.setDemo(true);
        accounts.saveAndFlush(user);
        mvc.perform(delete("/devices/{id}", targetDevice.getId()).header("Authorization", adminToken)
                        .header("X-View-As-Account", user.getId())).andExpect(status().isForbidden());
        mvc.perform(post("/devices/{id}/mqtt-relays/0", targetDevice.getId()).header("Authorization", adminToken)
                        .header("X-View-As-Account", user.getId()).contentType("application/json").content("{\"on\":true}"))
                .andExpect(status().isForbidden());
        assertTrue(devices.existsById(targetDevice.getId()));
        verifyNoInteractions(mqttService);
        assertNull(context.getAccountId());
    }

    @Test
    void adminRevocationTakesEffectOnNextSupportReadAndWrite() throws Exception {
        admin.setAdmin(false);
        accounts.saveAndFlush(admin);
        mvc.perform(get("/me").header("Authorization", adminToken).header("X-View-As-Account", user.getId()))
                .andExpect(status().isForbidden());
        mvc.perform(put("/me").header("Authorization", adminToken).header("X-View-As-Account", user.getId())
                        .contentType("application/json").content("{\"email\":\"changed@example.test\"}"))
                .andExpect(status().isForbidden());
        assertEquals("target@example.test", accounts.findById(user.getId()).orElseThrow().getEmail());
        assertNull(context.getAccountId());
    }

    private AccountEntity account(boolean isAdmin, String email) {
        return accounts.saveAndFlush(AccountEntity.builder().uuid(UUID.randomUUID())
                .secret(UUID.randomUUID().toString()).email(email).admin(isAdmin).build());
    }

    private String token(AccountEntity account) {
        String value = UUID.randomUUID().toString();
        tokens.saveAndFlush(TokenEntity.builder().account(account).token(value).build());
        return value;
    }
}
