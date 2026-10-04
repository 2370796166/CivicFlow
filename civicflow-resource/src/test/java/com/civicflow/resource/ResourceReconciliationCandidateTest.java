package com.civicflow.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.civicflow.resource.service.ResourceSlotSnapshotService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class ResourceReconciliationCandidateTest {
    @Autowired private JdbcTemplate db;
    @Autowired private ResourceSlotSnapshotService snapshots;

    @AfterEach
    void cleanup() {
        db.update("DELETE FROM resource_slot WHERE id BETWEEN 91001 AND 91003");
        db.update("DELETE FROM service_item WHERE id = 91002");
        db.update("DELETE FROM service_outlet WHERE id = 91001");
    }

    @Test
    void pagesReleasedOpenAndSuspendedSlotsWithinBusinessWindow() {
        Instant now = Instant.now();
        LocalDate date = LocalDate.ofInstant(now, ZoneId.of("Asia/Shanghai")).plusDays(1);
        db.update(
                "INSERT INTO service_outlet (id,code,name,address,status) VALUES "
                        + "(91001,'recon-outlet','outlet','address','ENABLED')");
        db.update(
                "INSERT INTO service_item (id,code,name,default_duration_minutes,status) "
                        + "VALUES (91002,'recon-item','item',30,'ENABLED')");
        insertSlot(
                91001, date, LocalTime.of(9, 0), LocalTime.of(10, 0), now.minusSeconds(60), "OPEN");
        insertSlot(
                91002,
                date,
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                now.minusSeconds(60),
                "SUSPENDED");
        insertSlot(
                91003,
                date,
                LocalTime.of(11, 0),
                LocalTime.of(12, 0),
                now.minusSeconds(60),
                "DRAFT");

        var first = snapshots.reconciliationCandidates(now, 0, 1);
        assertEquals(1, first.items().size());
        assertEquals("91001", first.items().get(0).slotId());
        assertTrue(first.hasMore());
        var second = snapshots.reconciliationCandidates(now, 91001, 1);
        assertEquals(1, second.items().size());
        assertEquals("91002", second.items().get(0).slotId());
        assertFalse(second.hasMore());
    }

    private void insertSlot(
            long id,
            LocalDate date,
            LocalTime start,
            LocalTime end,
            Instant release,
            String status) {
        db.update(
                "INSERT INTO resource_slot (id,outlet_id,item_id,service_date,start_time,"
                        + "end_time,total_quota,release_at,check_in_start,check_in_end,status,"
                        + "created_by,updated_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id,
                91001,
                91002,
                date,
                start,
                end,
                5,
                release,
                release,
                release.plusSeconds(3600),
                status,
                1,
                1);
    }
}
