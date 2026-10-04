package com.civicflow.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class QueueFlywayMySqlIntegrationTest {
    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4.11"))
                    .withDatabaseName("civicflow_queue")
                    .withUsername("queue_migration")
                    .withPassword("integration-only-password");

    @Test
    void freshMigrationsAndSkipLockedClaimDistinctTickets() throws Exception {
        var migration =
                Flyway.configure()
                        .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                        .locations("classpath:db/migration")
                        .load()
                        .migrate();
        assertEquals(3, migration.migrationsExecuted);
        try (Connection setup = MYSQL.createConnection("");
                Statement statement = setup.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO queue_ticket(id,appointment_id,user_id,outlet_id,item_id,service_date,ticket_no,priority,status,checked_in_at) VALUES(1,1001,2001,201,301,'2026-09-25','A001',0,'WAITING',UTC_TIMESTAMP(3)),(2,1002,2002,201,301,'2026-09-25','A002',1,'WAITING',UTC_TIMESTAMP(3))");
        }
        try (Connection first = MYSQL.createConnection("");
                Connection second = MYSQL.createConnection("")) {
            first.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            second.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            assertEquals(2L, lockNext(first));
            assertEquals(1L, lockNext(second));
            try (Statement a = first.createStatement();
                    Statement b = second.createStatement()) {
                assertEquals(
                        1,
                        a.executeUpdate(
                                "UPDATE queue_ticket SET status='CALLED',version=version+1 WHERE id=2 AND status='WAITING'"));
                assertEquals(
                        1,
                        b.executeUpdate(
                                "UPDATE queue_ticket SET status='CALLED',version=version+1 WHERE id=1 AND status='WAITING'"));
            }
            first.commit();
            second.commit();
        }
    }

    private static long lockNext(Connection connection) throws Exception {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT id FROM queue_ticket WHERE outlet_id=? AND service_date=? AND status='WAITING' AND item_id IN (?) ORDER BY priority DESC,checked_in_at,id LIMIT 1 FOR UPDATE SKIP LOCKED")) {
            statement.setLong(1, 201);
            statement.setDate(2, Date.valueOf(LocalDate.of(2026, 9, 25)));
            statement.setLong(3, 301);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new AssertionError("No unlocked ticket");
                return result.getLong(1);
            }
        }
    }
}
