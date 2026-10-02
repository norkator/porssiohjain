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
}
