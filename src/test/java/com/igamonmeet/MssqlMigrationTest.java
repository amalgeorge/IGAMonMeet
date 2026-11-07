package com.igamonmeet;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Applies every migration to a real SQL Server. Runs only when MSSQL_URL is set (CI service container). */
@EnabledIfEnvironmentVariable(named = "MSSQL_URL", matches = ".+")
class MssqlMigrationTest {

    @Test
    void allMigrationsApplyToSqlServer() {
        MigrateResult result = Flyway.configure()
                .dataSource(System.getenv("MSSQL_URL"), System.getenv("MSSQL_USER"), System.getenv("MSSQL_PASSWORD"))
                .locations("classpath:db/migration", "classpath:db/vendor/sqlserver")
                .load()
                .migrate();
        assertThat(result.success).isTrue();
    }
}
