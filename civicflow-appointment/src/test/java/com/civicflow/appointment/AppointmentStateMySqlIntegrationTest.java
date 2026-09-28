package com.civicflow.appointment;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the same state, CAS, and idempotency assertions on the actual Flyway schema. */
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class AppointmentStateMySqlIntegrationTest extends AppointmentStateIntegrationTest {
    @org.junit.jupiter.api.Test
    void repeatDeadlineRacesOneHundredTimesOnMySql() throws Exception {
        for (int iteration = 0; iteration < 100; iteration++) {
            setup();
            concurrentConfirmAndTimeoutHaveSingleWinner();
            setup();
            concurrentCancelAndTimeoutHaveSingleTerminalTransition();
        }
    }

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
        p.add("spring.sql.init.mode", () -> "never");
    }
}
