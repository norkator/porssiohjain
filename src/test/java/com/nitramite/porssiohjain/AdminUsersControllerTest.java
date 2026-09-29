package com.nitramite.porssiohjain;

import com.nitramite.porssiohjain.auth.AuthContext;
import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.TokenEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.TokenRepository;
import com.nitramite.porssiohjain.mqtt.MqttService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
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
    void previewBlocksWritesCredentialsExportAndAdminAccess() throws Exception {
        for (var request : java.util.List.of(put("/me"), delete("/me"), post("/me/password"),
                post("/devices/123/heat-pump/commands"), get("/me/export"), get("/api/admin/users"))) {
            mvc.perform(request.header("Authorization", adminToken).header("X-View-As-Account", user.getId()))
                    .andExpect(status().isForbidden());
            assertNull(context.getAccountId());
        }
    }

    @Test
    void adminRevocationTakesEffectOnNextPreviewRequest() throws Exception {
        admin.setAdmin(false);
        accounts.saveAndFlush(admin);
        mvc.perform(get("/me").header("Authorization", adminToken).header("X-View-As-Account", user.getId()))
                .andExpect(status().isForbidden());
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
