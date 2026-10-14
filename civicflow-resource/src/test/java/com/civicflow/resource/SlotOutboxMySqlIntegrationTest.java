package com.civicflow.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.civicflow.resource.entity.ResourceSlotOutboxEntity;
import com.civicflow.resource.mapper.ResourceSlotOutboxMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

@ActiveProfiles("test")
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class SlotOutboxMySqlIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.sql.init.mode", () -> "never");
    }

    @Autowired ResourceSlotOutboxMapper outbox;
    @Autowired JdbcTemplate jdbc;

    private void insert(long id, long slotId, Instant now) {
        var entry = new ResourceSlotOutboxEntity();
        entry.setId(id);
        entry.setEventId(java.util.UUID.randomUUID().toString());
        entry.setSlotId(slotId);
        entry.setEventType("resource.slot.changed");
        entry.setEventVersion(1);
        entry.setRoutingKey("resource.slot.changed.v1");
        entry.setPayloadJson("{}");
        entry.setStatus("PENDING");
        entry.setAttempts(0);
        entry.setNextAttemptAt(now.minusSeconds(1));
        outbox.insert(entry);
    }

    @Test
    void perSlotOrderingAndExpiredLeaseTakeoverFenceOldPublisherCompletion() {
        jdbc.update("DELETE FROM resource_slot_outbox");
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        insert(100, 1, now);
        insert(101, 1, now);
        insert(102, 2, now);
        assertEquals(
                java.util.List.of(100L, 102L),
                outbox.due(now, 20).stream().map(ResourceSlotOutboxEntity::getId).toList());
        assertEquals(1, outbox.claim(100, 0, now, now.plusSeconds(60)));
        assertEquals(0, outbox.claim(100, 0, now, now.plusSeconds(60)));
        assertEquals(1, outbox.claim(100, 1, now.plusSeconds(61), now.plusSeconds(121)));
        assertEquals(0, outbox.finish(100, 1, "PUBLISHED", now, now, null, now));
        assertEquals(1, outbox.finish(100, 2, "PUBLISHED", now, now, null, now));
        assertEquals(
                java.util.List.of(101L, 102L),
                outbox.due(now, 20).stream().map(ResourceSlotOutboxEntity::getId).toList());
    }
}
