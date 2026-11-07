package com.igamonmeet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.igamonmeet.account.RecoveryService;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Runs against a real SQL Server only when MSSQL_URL is set (CI service container). */
@EnabledIfEnvironmentVariable(named = "MSSQL_URL", matches = ".+")
class MssqlMigrationTest {

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(System.getenv("MSSQL_URL"), System.getenv("MSSQL_USER"), System.getenv("MSSQL_PASSWORD"));
    }

    @Test
    void migrationsAndSqlServerObjectsWork() throws Exception {
        MigrateResult result = Flyway.configure()
                .dataSource(System.getenv("MSSQL_URL"), System.getenv("MSSQL_USER"), System.getenv("MSSQL_PASSWORD"))
                .locations("classpath:db/migration", "classpath:db/vendor/sqlserver")
                .load()
                .migrate();
        assertThat(result.success).isTrue();

        try (Connection c = connect()) {
            // First Admin: procedure creates the user and a token whose hash Java can reproduce.
            String link = recover(c, "dba-first-admin");
            assertThat(link).contains("/recover#token=");
            String token = link.substring(link.indexOf("token=") + 6);
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM recovery_token WHERE token_hash = ? AND used_at IS NULL")) {
                ps.setString(1, RecoveryService.hash(token));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }
            // With an active Admin present, an unknown username is refused.
            assertThatThrownBy(() -> recover(c, "someone-else")).hasMessageContaining("No Admin has that username");

            // Audit rows cannot be changed or removed.
            assertThatThrownBy(() -> c.createStatement().executeUpdate("DELETE FROM audit_log"))
                    .hasMessageContaining("append-only");
        }
    }

    private static String recover(Connection c, String username) throws SQLException {
        try (CallableStatement cs = c.prepareCall("{call dbo.usp_AdminRecovery(?)}")) {
            cs.setString(1, username);
            try (ResultSet rs = cs.executeQuery()) {
                rs.next();
                return rs.getString("recovery_link");
            }
        }
    }
}
