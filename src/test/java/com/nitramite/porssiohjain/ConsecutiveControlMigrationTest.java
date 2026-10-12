package com.nitramite.porssiohjain;

import com.nitramite.porssiohjain.entity.enums.ControlMode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class ConsecutiveControlMigrationTest {
    @Test
    void migrationAcceptsAllModesAndPreservesExistingRowsAsNonBase() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:consecutive-control;MODE=PostgreSQL");
             var statement = connection.createStatement();
             var resource = getClass().getResourceAsStream("/db/migration/V108__add_consecutive_control_mode.sql")) {
            assertNotNull(resource);
            statement.execute("CREATE TABLE control (mode VARCHAR(50), CONSTRAINT mode_check CHECK (mode IN ('MANUAL')))");
            statement.execute("CREATE TABLE control_table (id BIGINT PRIMARY KEY)");
            statement.execute("INSERT INTO control VALUES ('MANUAL')");
            statement.execute("INSERT INTO control_table VALUES (1)");
            for (String sql : new String(resource.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                if (!sql.isBlank()) statement.execute(sql);
            }
            try (var insert = connection.prepareStatement("INSERT INTO control (mode) VALUES (?)")) {
                for (ControlMode mode : ControlMode.values()) {
                    insert.setString(1, mode.name());
                    insert.executeUpdate();
                }
            }
            assertThrows(SQLException.class, () -> statement.execute("INSERT INTO control VALUES ('INVALID')"));
            try (var rows = statement.executeQuery("SELECT consecutive_base FROM control_table WHERE id = 1")) {
                assertTrue(rows.next());
                assertFalse(rows.getBoolean(1));
            }
            statement.execute("INSERT INTO control_table (id, consecutive_base) VALUES (2, TRUE)");
            try (var rows = statement.executeQuery("SELECT consecutive_base FROM control_table WHERE id = 2")) {
                assertTrue(rows.next());
                assertTrue(rows.getBoolean(1));
            }
        }
    }
}
