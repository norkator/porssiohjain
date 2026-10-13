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
    @Autowired com.nitramite.porssiohjain.entity.repository.FeatureRequestCommentRepository comments;
    @Autowired com.nitramite.porssiohjain.services.FeatureRequestService service;
    @Autowired com.nitramite.porssiohjain.services.AccountDataExportService exports;
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
    @Test
    void listsOnlyOwnRequestsWithPaginationAndDeniesOtherOwnersConversation() throws Exception {
        service.submit(account.getId(), new com.nitramite.porssiohjain.services.models.FeatureRequestInput("Use", "My idea", ""));
        var other = accounts.saveAndFlush(AccountEntity.builder().uuid(UUID.randomUUID()).secret("other").build());
        service.submit(other.getId(), new com.nitramite.porssiohjain.services.models.FeatureRequestInput("Private", "Other idea", ""));
        var otherId = requests.findAll().stream().filter(row -> row.getAccountId().equals(other.getId())).findFirst().orElseThrow().getId();
        mvc.perform(get("/api/feature-requests").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].requestedChanges").value("My idea"));
        mvc.perform(get("/api/feature-requests/" + otherId).header("Authorization", token)).andExpect(status().isNotFound());
        mvc.perform(post("/api/feature-requests/" + otherId + "/comments").header("Authorization", token)
                .contentType("application/json").content("{\"message\":\"Not mine\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/feature-requests/" + otherId + "/read").header("Authorization", token)
                .contentType("application/json").content("{\"seenCommentId\":0}")).andExpect(status().isBadRequest());
        assertEquals(0, comments.count());
        mvc.perform(get("/api/feature-requests")).andExpect(status().isUnauthorized());
    }

    @Test
    void roundTripDiscussionKeepsOriginalAndNewRepliesUnreadUntilActuallySeen() throws Exception {
        mvc.perform(post("/api/feature-requests").header("Authorization", token).contentType("application/json").content(INPUT))
                .andExpect(status().isNoContent());
        var id = requests.findAll().getFirst().getId();
        var admin = accounts.saveAndFlush(AccountEntity.builder().uuid(UUID.randomUUID()).secret("admin").admin(true).build());
        service.comment(admin.getId(), id, "  Could you explain?  ", true);
        long first = comments.findByFeatureRequestIdOrderByIdAsc(id).getFirst().getId();
        assertEquals(1, service.countOwnUnread(account.getId()));
        assertEquals(0, service.countOwnUnread(admin.getId()));
        mvc.perform(get("/api/feature-requests").header("Authorization", token)).andExpect(jsonPath("$.items[0].unreadComments").value(true));
        mvc.perform(get("/api/feature-requests/" + id).header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.comments[0].message").value("Could you explain?"))
                .andExpect(jsonPath("$.comments[0].adminAuthor").value(true));
        // Merely querying a conversation (including preview) does not consume unread state.
        assertTrue(requests.findById(id).orElseThrow().hasUnreadComments());
        service.comment(admin.getId(), id, "A second thought", true);
        assertEquals(1, service.countOwnUnread(account.getId()));
        mvc.perform(post("/api/feature-requests/" + id + "/read").header("Authorization", token)
                .contentType("application/json").content("{\"seenCommentId\":" + first + "}")).andExpect(status().isNoContent());
        assertTrue(requests.findById(id).orElseThrow().hasUnreadComments());
        long latest = comments.findByFeatureRequestIdOrderByIdAsc(id).getLast().getId();
        mvc.perform(post("/api/feature-requests/" + id + "/read").header("Authorization", token)
                .contentType("application/json").content("{\"seenCommentId\":" + latest + "}")).andExpect(status().isNoContent());
        mvc.perform(post("/api/feature-requests/" + id + "/read").header("Authorization", token)
                .contentType("application/json").content("{\"seenCommentId\":" + first + "}")).andExpect(status().isNoContent());
        assertFalse(requests.findById(id).orElseThrow().hasUnreadComments());
        assertEquals(0, service.countOwnUnread(account.getId()));
        mvc.perform(post("/api/feature-requests/" + id + "/comments").header("Authorization", token)
                .contentType("application/json").content("{\"message\":\"Here is my answer\",\"adminAuthor\":true}")).andExpect(status().isNoContent());
        var detail = service.detail(admin.getId(), id, true);
        assertEquals(3, detail.comments().size());
        assertFalse(detail.comments().getLast().adminAuthor());
        try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(exports.exportAccountData(account.getId())))) {
            java.util.zip.ZipEntry entry;
            boolean found = false;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.getName().equals("account-data.json")) continue;
                var data = new com.fasterxml.jackson.databind.ObjectMapper().readTree(zip.readAllBytes());
                for (var table : data.get("tables")) {
                    if (!table.get("entity").asText().equals("FeatureRequestCommentEntity")) continue;
                    assertEquals(3, table.get("rowCount").asInt());
                    found = true;
                }
            }
            assertTrue(found, "Account export includes the full request discussion, including administrator replies");
        }
        assertEquals("Home heating", detail.useCase());
        assertEquals("Better scheduling", detail.requestedChanges());
        service.setHandled(admin.getId(), id, true);
        assertTrue(service.listForAdmin(admin.getId(), 0, false).isEmpty());
        assertEquals(1, service.listForAdmin(admin.getId(), 0, true).getTotalElements());
        service.comment(admin.getId(), id, "Handled, with a follow-up", true);
        assertEquals(1, service.countOwnUnread(account.getId()), "Handled requests with new replies still notify their owner");
        mvc.perform(get("/api/feature-requests").header("Authorization", token)).andExpect(jsonPath("$.items[0].handled").value(true));
        service.setHandled(admin.getId(), id, false);
        assertEquals(1, service.listForAdmin(admin.getId(), 0, false).getTotalElements());
    }

    @Test
    void rejectsInvalidCommentsAndReadMarkersAndReadOnlyWrites() throws Exception {
        service.submit(account.getId(), new com.nitramite.porssiohjain.services.models.FeatureRequestInput("Use", "Idea", ""));
        var id = requests.findAll().getFirst().getId();
        for (String message : java.util.List.of(" ", "x".repeat(5001))) {
            mvc.perform(post("/api/feature-requests/" + id + "/comments").header("Authorization", token)
                    .contentType("application/json").content("{\"message\":\"" + message + "\"}")).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/feature-requests/" + id + "/read").header("Authorization", token)
                .contentType("application/json").content("{\"seenCommentId\":123456789}")).andExpect(status().isBadRequest());
        assertThrows(IllegalArgumentException.class, () -> service.comment(account.getId(), id, "Fake admin", true));
        assertThrows(IllegalArgumentException.class, () -> service.setHandled(account.getId(), id, true));
        account.setDemo(true);
        accounts.saveAndFlush(account);
        for (String action : java.util.List.of("comments", "read")) {
            mvc.perform(post("/api/feature-requests/" + id + "/" + action).header("Authorization", token)
                    .contentType("application/json").content("{\"message\":\"Hi\",\"seenCommentId\":0}")).andExpect(status().isForbidden());
        }
        account.setDemo(false); account.setAdmin(true); accounts.saveAndFlush(account);
        var target = accounts.saveAndFlush(AccountEntity.builder().uuid(UUID.randomUUID()).secret("preview").build());
        for (String action : java.util.List.of("comments", "read")) {
            mvc.perform(post("/api/feature-requests/" + id + "/" + action).header("Authorization", token)
                    .header("X-View-As-Account", target.getId()).contentType("application/json")
                    .content("{\"message\":\"Hi\",\"seenCommentId\":0}")).andExpect(status().isForbidden());
        }
        assertEquals(0, comments.count());
    }

    @Test
    void ownHistoryIncludesAllPagesInNewestFirstOrder() throws Exception {
        for (int i = 0; i < 31; i++) service.submit(account.getId(), new com.nitramite.porssiohjain.services.models.FeatureRequestInput("Use", "Idea " + i, ""));
        mvc.perform(get("/api/feature-requests").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(30))
                .andExpect(jsonPath("$.items[0].requestedChanges").value("Idea 30"))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/feature-requests?page=1").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].requestedChanges").value("Idea 0"));
    }

}
