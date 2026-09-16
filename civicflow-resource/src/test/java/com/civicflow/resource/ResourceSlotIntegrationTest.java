package com.civicflow.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.civicflow.common.exception.BusinessException;
import com.civicflow.resource.dto.request.AdjustSlotQuotaRequest;
import com.civicflow.resource.dto.request.BatchCreateSlotsRequest;
import com.civicflow.resource.dto.request.ChangeSlotStatusRequest;
import com.civicflow.resource.dto.request.CreateSlotRequest;
import com.civicflow.resource.dto.response.SlotBatchResponse;
import com.civicflow.resource.dto.response.SlotResponse;
import com.civicflow.resource.enums.SlotStatus;
import com.civicflow.resource.error.ResourceErrorCode;
import com.civicflow.resource.service.SlotAdminService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class ResourceSlotIntegrationTest {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final long OUTLET_ID = 101;
    private static final long ITEM_ID = 201;

    @Autowired private SlotAdminService slotService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MockMvc mockMvc;

    @BeforeEach
    void resetAndSeed() {
        jdbcTemplate.update("DELETE FROM resource_slot_outbox");
        jdbcTemplate.update("DELETE FROM resource_slot_batch_result");
        jdbcTemplate.update("DELETE FROM resource_slot_day_lock");
        jdbcTemplate.update("DELETE FROM resource_admin_audit");
        jdbcTemplate.update("DELETE FROM resource_admin_idempotency");
        jdbcTemplate.update("DELETE FROM resource_slot");
        jdbcTemplate.update("DELETE FROM staff_window_scope");
        jdbcTemplate.update("DELETE FROM window_item_rel");
        jdbcTemplate.update("DELETE FROM service_window");
        jdbcTemplate.update("DELETE FROM service_item");
        jdbcTemplate.update("DELETE FROM service_outlet");
        jdbcTemplate.update(
                "INSERT INTO service_outlet (id,code,name,address,status,version,deleted) VALUES (?,?,?,?,?,?,?)",
                OUTLET_ID,
                "OUTLET_SLOT",
                "Slot outlet",
                "Shanghai",
                "ENABLED",
                0,
                0);
        jdbcTemplate.update(
                "INSERT INTO service_item (id,code,name,default_duration_minutes,status,version,deleted) VALUES (?,?,?,?,?,?,?)",
                ITEM_ID,
                "ITEM_SLOT",
                "Slot item",
                30,
                "ENABLED",
                0,
                0);
        jdbcTemplate.update(
                "INSERT INTO service_window (id,outlet_id,code,name,status,version,deleted) VALUES (?,?,?,?,?,?,?)",
                301,
                OUTLET_ID,
                "W01",
                "Window 01",
                "ENABLED",
                0,
                0);
        jdbcTemplate.update(
                "INSERT INTO window_item_rel (id,window_id,item_id,deleted) VALUES (?,?,?,?)",
                401,
                301,
                ITEM_ID,
                0);
    }

    @Test
    void createsQueriesAndRejectsOverlappingSlots() {
        LocalDate date = businessDate().plusDays(10);
        SlotResponse created =
                slotService.create(1, "create-slot", "request-create", draft(date, 9, 0, 10, 0));

        assertEquals("101", created.outletId());
        assertEquals(1, slotService.list(OUTLET_ID, ITEM_ID, date, date, null, 1, 20).total());

        BusinessException overlap =
                org.junit.jupiter.api.Assertions.assertThrows(
                        BusinessException.class,
                        () ->
                                slotService.create(
                                        1,
                                        "overlap-slot",
                                        "request-overlap",
                                        draft(date, 9, 30, 10, 30)));
        assertEquals(ResourceErrorCode.SLOT_OVERLAP, overlap.errorCode());

        slotService.create(1, "adjacent-slot", "request-adjacent", draft(date, 10, 0, 11, 0));
        assertEquals(2, slotService.list(OUTLET_ID, ITEM_ID, date, date, null, 1, 20).total());
    }

    @Test
    void slotWriteEndpointRequiresAdminAndSerializesIdsAsStrings() throws Exception {
        CreateSlotRequest request = draft(businessDate().plusDays(10), 9, 0, 10, 0);
        mockMvc.perform(
                        post("/api/v1/admin/slots")
                                .with(
                                        jwt().jwt(token -> token.subject("1"))
                                                .authorities(
                                                        new SimpleGrantedAuthority("ROLE_USER")))
                                .header("Idempotency-Key", "slot-api-forbidden")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403_FORBIDDEN"));

        mockMvc.perform(
                        post("/api/v1/admin/slots")
                                .with(
                                        jwt().jwt(token -> token.subject("1"))
                                                .authorities(
                                                        new SimpleGrantedAuthority("ROLE_ADMIN")))
                                .header("Idempotency-Key", "slot-api-create")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.outletId").value(Long.toString(OUTLET_ID)))
                .andExpect(jsonPath("$.data.itemId").value(Long.toString(ITEM_ID)));
    }

    @Test
    void batchReportsCreatedSkippedAndFailedAndReplaysResult() {
        LocalDate start = businessDate().plusDays(20);
        slotService.create(
                1, "batch-existing-exact", "request-exact", draft(start.plusDays(1), 9, 0, 10, 0));
        slotService.create(
                1,
                "batch-existing-overlap",
                "request-overlap",
                draft(start.plusDays(2), 9, 30, 10, 30));
        BatchCreateSlotsRequest request =
                new BatchCreateSlotsRequest(
                        Long.toString(OUTLET_ID),
                        Long.toString(ITEM_ID),
                        start,
                        start.plusDays(2),
                        LocalTime.of(9, 0),
                        LocalTime.of(10, 0),
                        20,
                        1,
                        LocalTime.of(8, 0),
                        LocalTime.of(8, 30),
                        LocalTime.of(9, 30),
                        SlotStatus.DRAFT);

        SlotBatchResponse first =
                slotService.batchCreate(1, "batch-create", "request-batch", request);
        SlotBatchResponse replay =
                slotService.batchCreate(1, "batch-create", "request-batch-retry", request);

        assertEquals(1, first.created());
        assertEquals(1, first.skipped());
        assertEquals(1, first.failed().size());
        assertEquals(ResourceErrorCode.SLOT_OVERLAP.code(), first.failed().get(0).code());
        assertEquals(first, replay);
        assertEquals(
                3,
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM resource_slot", Integer.class));
    }

    @Test
    void quotaChangeUsesConfigVersionAndTransactionalOutbox() throws Exception {
        LocalDate date = businessDate().plusDays(1);
        Instant releaseAt = Instant.now().minusSeconds(60);
        CreateSlotRequest request =
                request(
                        date,
                        LocalTime.of(9, 0),
                        LocalTime.of(10, 0),
                        10,
                        releaseAt,
                        SlotStatus.DRAFT);
        SlotResponse created = slotService.create(1, "released-draft", "request-released", request);

        BusinessException unknown =
                org.junit.jupiter.api.Assertions.assertThrows(
                        BusinessException.class,
                        () ->
                                slotService.adjustQuota(
                                        1,
                                        Long.parseLong(created.id()),
                                        "decrease-unknown",
                                        "request-decrease",
                                        new AdjustSlotQuotaRequest(9, 1)));
        assertEquals(ResourceErrorCode.CONSUMPTION_UNKNOWN, unknown.errorCode());

        SlotResponse increased =
                slotService.adjustQuota(
                        1,
                        Long.parseLong(created.id()),
                        "increase-released",
                        "request-increase",
                        new AdjustSlotQuotaRequest(12, 1));
        assertEquals(12, increased.totalQuota());
        assertEquals(2, increased.configVersion());
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM resource_slot_outbox WHERE slot_id = ? AND status = 'PENDING'",
                        Integer.class,
                        Long.parseLong(created.id())));
        String payload =
                jdbcTemplate.queryForObject(
                        "SELECT payload_json FROM resource_slot_outbox WHERE slot_id = ?",
                        String.class,
                        Long.parseLong(created.id()));
        if (payload.startsWith("\"") && payload.endsWith("\"")) {
            payload = objectMapper.readValue(payload, String.class);
        }
        assertTrue(payload.contains("\"oldTotalQuota\":10"));
        assertTrue(payload.contains("\"newTotalQuota\":12"));
        assertTrue(payload.contains("\"configVersion\":2"));

        jdbcTemplate.update(
                "UPDATE resource_slot SET consumed_hint = 8 WHERE id = ?",
                Long.parseLong(created.id()));
        BusinessException belowConsumed =
                org.junit.jupiter.api.Assertions.assertThrows(
                        BusinessException.class,
                        () ->
                                slotService.adjustQuota(
                                        1,
                                        Long.parseLong(created.id()),
                                        "below-consumed",
                                        "request-below",
                                        new AdjustSlotQuotaRequest(7, 2)));
        assertEquals(ResourceErrorCode.QUOTA_BELOW_CONSUMED, belowConsumed.errorCode());
    }

    @Test
    void statusTransitionsProduceVersionedEvents() {
        LocalDate date = businessDate().plusDays(10);
        SlotResponse created =
                slotService.create(
                        1, "status-slot", "request-status-create", draft(date, 9, 0, 10, 0));
        SlotResponse scheduled =
                slotService.changeStatus(
                        1,
                        Long.parseLong(created.id()),
                        "schedule-slot",
                        "request-schedule",
                        new ChangeSlotStatusRequest(SlotStatus.SCHEDULED, 0));
        SlotResponse suspended =
                slotService.changeStatus(
                        1,
                        Long.parseLong(created.id()),
                        "suspend-slot",
                        "request-suspend",
                        new ChangeSlotStatusRequest(SlotStatus.SUSPENDED, 1));

        assertEquals(SlotStatus.SCHEDULED, scheduled.status());
        assertEquals(2, scheduled.configVersion());
        assertEquals(SlotStatus.SUSPENDED, suspended.status());
        assertEquals(3, suspended.configVersion());
        assertEquals(
                2,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM resource_slot_outbox WHERE slot_id = ?",
                        Integer.class,
                        Long.parseLong(created.id())));
    }

    @Test
    void concurrentOverlappingCreatesHaveOnlyOneWinner() throws Exception {
        LocalDate date = businessDate().plusDays(30);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            futures.add(
                    executor.submit(
                            () ->
                                    createConcurrently(
                                            ready,
                                            start,
                                            "concurrent-a",
                                            draft(date, 9, 0, 10, 0))));
            futures.add(
                    executor.submit(
                            () ->
                                    createConcurrently(
                                            ready,
                                            start,
                                            "concurrent-b",
                                            draft(date, 9, 30, 10, 30))));
            ready.await();
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                try {
                    results.add(future.get());
                } catch (ExecutionException exception) {
                    results.add(exception.getCause());
                }
            }
            assertEquals(1, results.stream().filter(SlotResponse.class::isInstance).count());
            Object failure =
                    results.stream()
                            .filter(BusinessException.class::isInstance)
                            .findFirst()
                            .orElseThrow();
            BusinessException businessException =
                    assertInstanceOf(BusinessException.class, failure);
            assertEquals(ResourceErrorCode.SLOT_OVERLAP, businessException.errorCode());
            assertEquals(
                    1,
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM resource_slot", Integer.class));
        } finally {
            executor.shutdownNow();
        }
    }

    private Object createConcurrently(
            CountDownLatch ready, CountDownLatch start, String key, CreateSlotRequest request)
            throws InterruptedException {
        ready.countDown();
        start.await();
        return slotService.create(1, key, "request-" + key, request);
    }

    private CreateSlotRequest draft(
            LocalDate date, int startHour, int startMinute, int endHour, int endMinute) {
        LocalTime start = LocalTime.of(startHour, startMinute);
        LocalTime end = LocalTime.of(endHour, endMinute);
        return request(
                date,
                start,
                end,
                20,
                date.minusDays(1).atTime(8, 0).atZone(BUSINESS_ZONE).toInstant(),
                SlotStatus.DRAFT);
    }

    private CreateSlotRequest request(
            LocalDate date,
            LocalTime start,
            LocalTime end,
            int quota,
            Instant releaseAt,
            SlotStatus status) {
        return new CreateSlotRequest(
                Long.toString(OUTLET_ID),
                Long.toString(ITEM_ID),
                date,
                start,
                end,
                quota,
                releaseAt,
                date.atTime(start.minusMinutes(30)).atZone(BUSINESS_ZONE).toInstant(),
                date.atTime(start.plusMinutes(30)).atZone(BUSINESS_ZONE).toInstant(),
                status);
    }

    private LocalDate businessDate() {
        return LocalDate.now(BUSINESS_ZONE);
    }
}
