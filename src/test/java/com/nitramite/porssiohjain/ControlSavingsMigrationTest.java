package com.nitramite.porssiohjain;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class ControlSavingsMigrationTest {
    @Test
    void migrationPreservesExistingDataMakesMonthUniqueAndDeletesSavingsWithAccount() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:control-savings-migration;MODE=PostgreSQL");
             var statement = connection.createStatement();
             var resource = getClass().getResourceAsStream("/db/migration/V124__monthly_control_savings_and_token_retention.sql")) {
            statement.execute("CREATE TABLE account (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE control (id BIGINT PRIMARY KEY, account_id BIGINT)");
            statement.execute("CREATE TABLE control_table (id BIGINT PRIMARY KEY, end_time TIMESTAMP)");
            statement.execute("CREATE TABLE qr_login_challenge (id BIGINT PRIMARY KEY, expires_at TIMESTAMP)");
            statement.execute("CREATE TABLE push_notification_token (id BIGINT PRIMARY KEY, invalidated_at TIMESTAMP)");
            statement.execute("INSERT INTO account VALUES (1), (2)");
            statement.execute("INSERT INTO control_table VALUES (1, CURRENT_TIMESTAMP)");
            assertNotNull(resource);
            for (String sql : new String(resource.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                if (!sql.isBlank()) statement.execute(sql);
            }
            String row = "INSERT INTO control_savings_month (account_id, month_start, timezone, period_start, period_end, summary_json, calculated_at) "
                    + "VALUES (%d, '2026-03-01', 'Europe/Helsinki', '2026-02-28 22:00:00+00', '2026-03-31 21:00:00+00', '{}', CURRENT_TIMESTAMP)";
            statement.execute(row.formatted(1));
            statement.execute(row.formatted(2));
            assertThrows(SQLException.class, () -> statement.execute(row.formatted(1)));
            statement.execute("DELETE FROM account WHERE id = 1");
            try (var rows = statement.executeQuery("SELECT account_id FROM control_savings_month")) {
                assertTrue(rows.next());
                assertEquals(2, rows.getLong(1));
                assertFalse(rows.next());
            }
            try (var rows = statement.executeQuery("SELECT count(*) FROM control_table")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
            }
        }
    }
}
