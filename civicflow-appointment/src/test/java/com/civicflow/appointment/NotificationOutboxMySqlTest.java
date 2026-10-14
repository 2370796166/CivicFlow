package com.civicflow.appointment;

import static org.assertj.core.api.Assertions.*;

import com.civicflow.appointment.mapper.OutboxEventMapper;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(
        properties = {
            "spring.flyway.enabled=true",
            "spring.sql.init.mode=never",
            "civicflow.appointment.notification-publisher-enabled=false"
        })
@org.springframework.test.annotation.DirtiesContext
class NotificationOutboxMySqlTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () ->
                        MYSQL.getJdbcUrl()
                                + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired OutboxEventMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void concurrentClaimsAndLateOldCompletionCannotOverwriteNewAttempt() throws Exception {
        jdbc.update(
                "INSERT INTO outbox_event(id,event_id,aggregate_type,aggregate_id,event_type,event_version,routing_key,payload_json,status,attempts,next_attempt_at,last_error_code) VALUES(501,?,'test','1','appointment.notification.created',1,'test.v1','{}','PENDING',0,CURRENT_TIMESTAMP(3),'OLD_FAILURE')",
                UUID.randomUUID().toString());
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var a =
                    pool.submit(
                            () -> {
                                start.await();
                                return mapper.claimNotification(501, 0);
                            });
            var b =
                    pool.submit(
                            () -> {
                                start.await();
                                return mapper.claimNotification(501, 0);
                            });
            start.countDown();
            assertThat(a.get(5, TimeUnit.SECONDS) + b.get(5, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        jdbc.update(
                "UPDATE outbox_event SET updated_at=DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 31 SECOND) WHERE id=501");
        assertThat(mapper.claimNotification(501, 1)).isEqualTo(1);
        assertThat(mapper.claimNotification(501, 0)).isZero();
        assertThat(mapper.finishNotification(501, 1, "FAILED", null, "PUBLISH_UNKNOWN")).isZero();
        assertThat(mapper.finishNotification(501, 2, "PUBLISHED", Instant.now(), null))
                .isEqualTo(1);
        assertThat(mapper.finishNotification(501, 1, "FAILED", null, "PUBLISH_UNKNOWN")).isZero();
        var row =
                jdbc.queryForMap(
                        "SELECT status,attempts,last_error_code,published_at FROM outbox_event WHERE id=501");
        assertThat(row.get("status")).isEqualTo("PUBLISHED");
        assertThat(((Number) row.get("attempts")).intValue()).isEqualTo(2);
        assertThat(row.get("last_error_code")).isNull();
        assertThat(row.get("published_at")).isNotNull();
    }
}
