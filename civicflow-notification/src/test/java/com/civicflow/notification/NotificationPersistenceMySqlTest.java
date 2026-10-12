package com.civicflow.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.civicflow.common.exception.BusinessException;
import com.civicflow.notification.entity.NotificationRecordEntity;
import com.civicflow.notification.event.NotificationEvent;
import com.civicflow.notification.mapper.NotificationRecordMapper;
import com.civicflow.notification.service.NotificationService;
import com.civicflow.notification.service.NotificationStatus;
import com.civicflow.notification.service.impl.NotificationDeliveryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
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
@SpringBootTest(properties = "spring.flyway.enabled=true")
class NotificationPersistenceMySqlTest {
    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.11")
                    .withDatabaseName("civicflow_notification")
                    .withUsername("notification_test")
                    .withPassword("notification_test_password");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.url", MYSQL::getJdbcUrl);
        registry.add("spring.flyway.user", MYSQL::getUsername);
        registry.add("spring.flyway.password", MYSQL::getPassword);
    }

    @Autowired private NotificationService service;
    @Autowired private NotificationRecordMapper records;
    @Autowired private NotificationDeliveryService delivery;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void duplicateDeliveryAndOptionalPreferenceRemainIsolated() throws Exception {
        NotificationEvent required =
                event(
                        "appointment.notification.created",
                        "APPOINTMENT_CREATED",
                        Map.of("appointmentId", "42"),
                        true);
        String message = json.writeValueAsString(required);
        service.consume(required, message);
        service.consume(required, message);
        NotificationRecordEntity sent = find(required.eventId());
        assertThat(sent.getStatus()).isEqualTo(NotificationStatus.SENT.name());
        assertThat(
                        records.selectCount(
                                Wrappers.<NotificationRecordEntity>lambdaQuery()
                                        .eq(
                                                NotificationRecordEntity::getEventId,
                                                required.eventId())))
                .isEqualTo(1);
        assertThat(service.own(12, 1, 20).items())
                .filteredOn(item -> item.eventId().equals(required.eventId()))
                .hasSize(1);
        assertThatThrownBy(() -> service.read(13, sent.getId()))
                .isInstanceOf(BusinessException.class);

        records.update(
                null,
                Wrappers.<NotificationRecordEntity>lambdaUpdate()
                        .eq(NotificationRecordEntity::getId, sent.getId())
                        .set(NotificationRecordEntity::getStatus, NotificationStatus.DEAD.name()));
        assertThat(service.failures(1, 20).items())
                .filteredOn(item -> item.eventId().equals(required.eventId()))
                .hasSize(1);
        service.replay(99, sent.getId(), "manual recovery test");
        assertThat(records.selectById(sent.getId()).getStatus())
                .isEqualTo(NotificationStatus.SENT.name());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM notification_replay_audit WHERE record_id=?",
                                Integer.class,
                                sent.getId()))
                .isEqualTo(1);

        service.preference(12, false);
        NotificationEvent optional =
                event(
                        "queue.notification.missed",
                        "QUEUE_MISSED",
                        Map.of("ticketNo", "A001"),
                        false);
        service.consume(optional, json.writeValueAsString(optional));
        assertThat(find(optional.eventId()).getStatus())
                .isEqualTo(NotificationStatus.SKIPPED.name());
        assertThat(service.own(12, 1, 20).items())
                .filteredOn(item -> item.eventId().equals(required.eventId()))
                .hasSize(1);
    }

    @Test
    void expiredThirdClaimEndsInDeadWithoutAnotherAttempt() throws Exception {
        NotificationEvent event =
                event(
                        "appointment.notification.created",
                        "APPOINTMENT_CREATED",
                        Map.of("appointmentId", "43"),
                        true);
        service.consume(event, json.writeValueAsString(event));
        NotificationRecordEntity record = find(event.eventId());
        jdbc.update(
                "UPDATE notification_record SET status='PROCESSING',attempts=3,updated_at=DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 3 MINUTE) WHERE id=?",
                record.getId());

        assertThat(delivery.recoverStale()).isEqualTo(1);
        assertThat(records.selectById(record.getId()).getStatus())
                .isEqualTo(NotificationStatus.DEAD.name());
        assertThat(records.selectById(record.getId()).getAttempts()).isEqualTo(3);
    }

    private NotificationRecordEntity find(String eventId) {
        return records.selectOne(
                Wrappers.<NotificationRecordEntity>lambdaQuery()
                        .eq(NotificationRecordEntity::getEventId, eventId));
    }

    private static NotificationEvent event(
            String type, String template, Map<String, String> variables, boolean required) {
        String id = UUID.randomUUID().toString();
        return new NotificationEvent(
                1,
                id,
                type,
                1,
                Instant.now(),
                type.startsWith("queue.") ? "civicflow-queue" : "civicflow-appointment",
                id,
                "42",
                id,
                new NotificationEvent.Payload("12", "42", template, 1, variables, required));
    }
}
