package com.civicflow.queue;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Reuses workbench assertions with real MySQL locks and a fresh Flyway schema. */
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
@Sql(
        statements = {
            "DELETE FROM queue_state_sync", "DELETE FROM queue_command_idempotency",
            "DELETE FROM active_window_session_guard", "DELETE FROM window_work_session",
            "DELETE FROM queue_operation_log", "DELETE FROM queue_ticket"
        })
class QueueWorkbenchMySqlIntegrationTest extends QueueWorkbenchIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry p) {
        p.add(
                "spring.datasource.url",
                () ->
                        MYSQL.getJdbcUrl()
                                + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        p.add("spring.datasource.username", MYSQL::getUsername);
        p.add("spring.datasource.password", MYSQL::getPassword);
        p.add("spring.flyway.enabled", () -> true);
    }
}
