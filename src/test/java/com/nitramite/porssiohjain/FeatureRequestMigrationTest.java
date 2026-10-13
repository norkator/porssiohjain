package com.nitramite.porssiohjain;

import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class FeatureRequestMigrationTest {
    @Test
    void migrationPreservesResponsesUntilTheirAccountIsDeleted() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:feature-request-migration;MODE=PostgreSQL");
             var statement = connection.createStatement();
             var resource = getClass().getResourceAsStream("/db/migration/V107__create_feature_request.sql")) {
            assertNotNull(resource);
            statement.execute("CREATE TABLE account (id BIGINT PRIMARY KEY)");
            for (String sql : new String(resource.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                if (!sql.isBlank()) statement.execute(sql);
            }
            statement.execute("INSERT INTO account VALUES (1)");
            statement.execute("INSERT INTO feature_request (account_id, use_case, requested_changes, created_at) VALUES (1, 'Use', 'Changes', CURRENT_TIMESTAMP)");
            try (var rows = statement.executeQuery("SELECT * FROM feature_request")) {
                assertTrue(rows.next());
                assertNotNull(rows.getObject("created_at"));
                assertNull(rows.getString("contact_email"));
            }
            statement.execute("DELETE FROM account WHERE id = 1");
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM feature_request")) {
                rows.next();
                assertEquals(0, rows.getInt(1));
            }
        }
    }
    @Test
    void conversationMigrationPreservesOldRequestsAndCascadesTheirComments() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:feature-request-conversation;MODE=PostgreSQL");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE account (id BIGINT PRIMARY KEY)");
            for (String migration : java.util.List.of("V107__create_feature_request.sql", "V109__feature_request_conversations.sql")) {
                try (var resource = getClass().getResourceAsStream("/db/migration/" + migration)) {
                    assertNotNull(resource);
                    for (String sql : new String(resource.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                        if (!sql.isBlank()) statement.execute(sql);
                    }
                }
                if (migration.startsWith("V107")) {
                    statement.execute("INSERT INTO account VALUES (1), (2)");
                    statement.execute("INSERT INTO feature_request (id, account_id, use_case, requested_changes, created_at) VALUES (1, 1, 'Use', 'Idea', CURRENT_TIMESTAMP)");
                }
            }
            try (var rows = statement.executeQuery("SELECT * FROM feature_request")) {
                assertTrue(rows.next());
                assertFalse(rows.getBoolean("handled"));
                assertEquals(0, rows.getLong("latest_admin_comment_id"));
                assertEquals(0, rows.getLong("user_read_comment_id"));
            }
            statement.execute("INSERT INTO feature_request_comment (request_id, account_id, admin_author, message, created_at) VALUES (1, 2, TRUE, 'Reply', CURRENT_TIMESTAMP)");
            statement.execute("DELETE FROM account WHERE id = 2");
            try (var rows = statement.executeQuery("SELECT * FROM feature_request_comment")) {
                assertTrue(rows.next());
                assertNull(rows.getObject("account_id"));
                assertEquals("Reply", rows.getString("message"));
            }
            statement.execute("DELETE FROM account WHERE id = 1");
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM feature_request_comment")) {
                rows.next(); assertEquals(0, rows.getInt(1));
            }
        }
    }

}
