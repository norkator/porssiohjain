package com.nitramite.porssiohjain;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.TokenEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.TokenRepository;
import com.nitramite.porssiohjain.entity.repository.FeatureRequestRepository;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FeatureRequestsControllerTest {
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired TokenRepository tokens;
    @Autowired FeatureRequestRepository requests;
    @MockitoBean MqttService mqttService;
    private AccountEntity account;
    private String token;
    private static final String INPUT = """
            {"useCase":"Home heating", "requestedChanges":"Better scheduling"}
            """;

    @BeforeEach
    void setup() {
        account = accounts.saveAndFlush(AccountEntity.builder().uuid(UUID.randomUUID())
                .secret(UUID.randomUUID().toString()).email("owner@example.com").build());
        token = UUID.randomUUID().toString();
        tokens.saveAndFlush(TokenEntity.builder().account(account).token(token).build());
    }

    @Test
    void authenticatedSubmissionIsPersistedWithAccountAndEmail() throws Exception {
        mvc.perform(post("/api/feature-requests").header("Authorization", token)
                .contentType("application/json").content(INPUT)).andExpect(status().isNoContent());
        var row = requests.findAll().getFirst();
        assertEquals(account.getId(), row.getAccountId());
        assertEquals("owner@example.com", row.getContactEmail());
        assertEquals("Home heating", row.getUseCase());
        assertNotNull(row.getCreatedAt());
    }

    @Test
    void noEmailIsRequiredAndInvalidSubmissionsAreRejected() throws Exception {
        account.setEmail(null);
        accounts.saveAndFlush(account);
        mvc.perform(post("/api/feature-requests").header("Authorization", token)
                .contentType("application/json").content(INPUT)).andExpect(status().isNoContent());
        assertNull(requests.findAll().getFirst().getContactEmail());
        mvc.perform(post("/api/feature-requests").header("Authorization", token)
                .contentType("application/json").content("{\"useCase\":\" \"}"))
                .andExpect(status().isBadRequest());
        assertEquals(1, requests.count());
    }

    @Test
    void authenticationDemoAndPreviewRulesApply() throws Exception {
        mvc.perform(post("/api/feature-requests").contentType("application/json").content(INPUT))
                .andExpect(status().isUnauthorized());
        account.setDemo(true);
        accounts.saveAndFlush(account);
        mvc.perform(post("/api/feature-requests").header("Authorization", token)
                .contentType("application/json").content(INPUT)).andExpect(status().isForbidden());
        account.setDemo(false);
        account.setAdmin(true);
        accounts.saveAndFlush(account);
        var target = accounts.saveAndFlush(AccountEntity.builder().uuid(UUID.randomUUID())
                .secret(UUID.randomUUID().toString()).build());
        mvc.perform(post("/api/feature-requests").header("Authorization", token)
                .header("X-View-As-Account", target.getId()).contentType("application/json").content(INPUT))
                .andExpect(status().isForbidden());
        assertEquals(0, requests.count());
    }
}
