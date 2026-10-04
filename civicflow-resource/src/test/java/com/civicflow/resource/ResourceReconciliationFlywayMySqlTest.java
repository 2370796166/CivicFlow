package com.civicflow.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class ResourceReconciliationFlywayMySqlTest {
    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4.11"))
                    .withDatabaseName("civicflow_resource")
                    .withUsername("resource_migration")
                    .withPassword("integration-only-password");

    @Test
    void freshSchemaIncludesReconciliationCandidateIndex() throws Exception {
        var result =
                Flyway.configure()
                        .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                        .locations("classpath:db/migration")
                        .load()
                        .migrate();
        assertEquals(4, result.migrationsExecuted);
        try (Connection connection = MYSQL.createConnection("");
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT COUNT(*) FROM information_schema.statistics "
                                        + "WHERE table_schema = DATABASE() AND table_name = 'resource_slot' "
                                        + "AND index_name = 'idx_slot_reconciliation'")) {
            rows.next();
            assertEquals(4, rows.getInt(1));
        }
    }
}
