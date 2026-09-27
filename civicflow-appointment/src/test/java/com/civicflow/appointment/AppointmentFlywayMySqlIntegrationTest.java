package com.civicflow.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
class AppointmentFlywayMySqlIntegrationTest {
    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4.11"))
                    .withDatabaseName("civicflow_appointment")
                    .withUsername("appointment_migration")
                    .withPassword("integration-only-password");

    @Test
    void migratesFreshMySqlSchemaIncludingReservationProjection() throws Exception {
        var result =
                Flyway.configure()
                        .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                        .locations("classpath:db/migration")
                        .load()
                        .migrate();
        assertEquals(5, result.migrationsExecuted);

        try (Connection connection = MYSQL.createConnection("");
                Statement statement = connection.createStatement();
                ResultSet resultSet =
                        statement.executeQuery(
                                "SELECT COUNT(*) FROM information_schema.tables "
                                        + "WHERE table_schema = DATABASE() "
                                        + "AND table_name IN "
                                        + "('stock_admin_audit','appointment_reservation_request',"
                                        + "'appointment_command_idempotency','stock_reconciliation_run',"
                                        + "'stock_reconciliation_detail')")) {
            assertTrue(resultSet.next());
            assertEquals(5, resultSet.getInt(1));
        }
    }
}
