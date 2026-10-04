package com.civicflow.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.civicflow.appointment.client.ResourceSlotClient;
import com.civicflow.appointment.client.ResourceSlotSnapshot;
import com.civicflow.appointment.enums.ReconciliationClassification;
import com.civicflow.appointment.enums.ReconciliationRepairStatus;
import com.civicflow.appointment.service.StockReconciliationService;
import com.civicflow.appointment.support.RedisKeyFactory;
import com.civicflow.appointment.support.RedisStockRepository;
import com.civicflow.appointment.support.SlotStockSnapshot;
import com.civicflow.common.api.ApiResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class StockReconciliationIntegrationTest {
    private static final long SLOT_ID = 73001;
    private static final long ITEM_ID = 73002;
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Instant NOW = Instant.now();
    private static final Instant RELEASE = NOW.minusSeconds(3600);
    private static final Instant CLOSE = NOW.plusSeconds(7200);
    private static final LocalDate DATE = LocalDate.ofInstant(CLOSE, SHANGHAI);

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4.11-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired private JdbcTemplate db;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private RedisStockRepository stocks;
    @Autowired private StockReconciliationService reconciliation;
    @Autowired private MockMvc mockMvc;
    @MockitoBean private ResourceSlotClient resource;

    private final RedisKeyFactory keys = new RedisKeyFactory("dev");
    private final SlotStockSnapshot slot =
            new SlotStockSnapshot(SLOT_ID, 73003, ITEM_ID, DATE, 5, RELEASE, CLOSE, "OPEN", 3);

    @BeforeEach
    void seed() {
        db.update("DELETE FROM stock_reconciliation_detail");
        db.update("DELETE FROM stock_reconciliation_run");
        db.update("DELETE FROM stock_admin_audit");
        db.update("DELETE FROM stock_release_record");
        db.update("DELETE FROM active_booking_guard");
        db.update("DELETE FROM appointment_order");
        db.update("DELETE FROM appointment_reservation_request");
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        ResourceSlotSnapshot snapshot =
                new ResourceSlotSnapshot(
                        Long.toString(SLOT_ID),
                        "73003",
                        Long.toString(ITEM_ID),
                        "outlet",
                        "item",
                        DATE,
                        LocalTime.of(9, 0),
                        LocalTime.of(17, 0),
                        5,
                        RELEASE,
                        RELEASE,
                        CLOSE,
                        CLOSE,
                        "OPEN",
                        3);
        when(resource.getSnapshot(anyLong())).thenReturn(ApiResponse.success(snapshot, "resource"));
        assertEquals(
                0,
                stocks.initialize(slot, RELEASE.minusSeconds(10), CLOSE.plusSeconds(86400)).code());
    }

    @AfterEach
    void clearRedis() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void publishFailedAndCompensatedIsConsistent() {
        String id = reserve(101);
        request(id, 101, "FAILED");
        assertEquals(
                0,
                stocks.compensate(slot, 101, id, "PUBLISH_FAILED", NOW, CLOSE.plusSeconds(86400))
                        .code());
        release(id, 101, "SUCCEEDED");

        StockReconciliationService.Report report = inspect(true);
        assertEquals(5, report.expected());
        assertEquals(ReconciliationClassification.CONSISTENT, report.classification());
        assertEquals(ReconciliationRepairStatus.NOT_REQUIRED, report.repairStatus());
    }

    @Test
    void failedCompensationIsAlertedWithoutIncreasingStock() {
        String id = reserve(102);
        request(id, 102, "COMPENSATION_PENDING");
        release(id, 102, "FAILED");

        StockReconciliationService.Report report = inspect(true);
        assertEquals(4, report.expected());
        assertEquals(ReconciliationClassification.COMPENSATION_PENDING, report.classification());
        assertFalse(report.autoRepairable());
        assertEquals(4L, stocks.reconciliationSnapshot(slot).remaining());
    }

    @Test
    void inFlightReservationWithinGracePeriodIsObservedOnly() {
        String id = reserve(108);
        request(id, 108, "PUBLISHED");
        db.update(
                "UPDATE appointment_reservation_request SET updated_at = CURRENT_TIMESTAMP "
                        + "WHERE reservation_id = ?",
                id);

        StockReconciliationService.Report report = inspect(true);
        assertEquals(ReconciliationClassification.TRANSIENT_PENDING, report.classification());
        assertFalse(report.autoRepairable());
        assertEquals(4L, stocks.reconciliationSnapshot(slot).remaining());
    }

    @Test
    void duplicateConsumerDoesNotDoubleCountOrder() {
        String id = persisted(103);
        // The unique reservation id is the durable consumer idempotency boundary.
        assertEquals(
                1,
                db.queryForObject(
                        "SELECT COUNT(*) FROM appointment_order WHERE reservation_id = ?",
                        Integer.class,
                        id));
        StockReconciliationService.Report report = inspect(true);
        assertEquals(4, report.expected());
        assertEquals(ReconciliationClassification.CONSISTENT, report.classification());
    }

    @Test
    void lostRedisStockIsEvidenceMissingAndNotRebuilt() {
        persisted(104);
        redisTemplate.delete(keys.stockKeys(ITEM_ID, DATE, SLOT_ID).stock());

        StockReconciliationService.Report report = inspect(true);
        assertEquals(ReconciliationClassification.EVIDENCE_MISSING, report.classification());
        assertNull(report.actual());
        assertFalse(report.autoRepairable());
        assertFalse(
                Boolean.TRUE.equals(
                        redisTemplate.hasKey(keys.stockKeys(ITEM_ID, DATE, SLOT_ID).stock())));
    }

    @Test
    void missingActiveKeyIsReportedWithoutUnsafeRecreation() {
        String id = persisted(105);
        redisTemplate.delete(keys.reservationKeys(ITEM_ID, DATE, SLOT_ID, 105, id).active());

        StockReconciliationService.Report report = inspect(true);
        assertEquals(
                ReconciliationClassification.DB_ORDER_REDIS_ACTIVE_MISSING,
                report.classification());
        assertFalse(report.autoRepairable());
    }

    @Test
    void missingDatabaseActiveGuardBlocksInventoryRepair() {
        persisted(109);
        db.update("DELETE FROM active_booking_guard WHERE user_id = ?", 109);
        redisTemplate
                .opsForHash()
                .put(keys.stockKeys(ITEM_ID, DATE, SLOT_ID).stock(), "remaining", "5");

        StockReconciliationService.Report report = inspect(true);
        assertEquals(ReconciliationClassification.EVIDENCE_MISSING, report.classification());
        assertFalse(report.autoRepairable());
        assertEquals(5L, stocks.reconciliationSnapshot(slot).remaining());
    }

    @Test
    void erroneousInventoryIncreaseIsRepairedWithAuditAndCas() {
        persisted(106);
        redisTemplate
                .opsForHash()
                .put(keys.stockKeys(ITEM_ID, DATE, SLOT_ID).stock(), "remaining", "5");

        StockReconciliationService.Report report = inspect(true);
        assertEquals(ReconciliationClassification.REDIS_STOCK_MISMATCH, report.classification());
        assertTrue(report.autoRepairable());
        assertEquals(ReconciliationRepairStatus.REPAIRED, report.repairStatus());
        assertEquals(4L, stocks.reconciliationSnapshot(slot).remaining());
        assertEquals(
                "APPLIED",
                db.queryForObject(
                        "SELECT outcome FROM stock_admin_audit WHERE action = 'RECONCILIATION_REPAIR'",
                        String.class));
    }

    @Test
    void changedMutationSequenceRejectsStaleRepair() {
        var before = stocks.reconciliationSnapshot(slot);
        reserve(107);
        assertEquals(1, stocks.reconciliationRepair(slot, before, 5, NOW).code());
        assertEquals(4L, stocks.reconciliationSnapshot(slot).remaining());
    }

    @Test
    void manualEndpointRequiresAdminAndBindsIdempotencyKey() throws Exception {
        String endpoint = "/api/v1/admin/reconciliations";
        mockMvc.perform(
                        post(endpoint)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", "same-key")
                                .content("{\"slotId\":73001,\"repair\":false}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(
                        post(endpoint)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", "same-key")
                                .content("{\"slotId\":73001,\"repair\":false}")
                                .with(jwt()))
                .andExpect(status().isForbidden());
        var admin = jwt().jwt(token -> token.subject("9001")).authorities(() -> "ROLE_ADMIN");
        mockMvc.perform(
                        post(endpoint)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", "same-key")
                                .content("{\"slotId\":73001,\"repair\":false}")
                                .with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.classification").value("CONSISTENT"));
        mockMvc.perform(
                        post(endpoint)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", "same-key")
                                .content("{\"slotId\":73001,\"repair\":false}")
                                .with(admin))
                .andExpect(status().isOk());
        assertEquals(
                1,
                db.queryForObject("SELECT COUNT(*) FROM stock_reconciliation_run", Integer.class));
        mockMvc.perform(
                        post(endpoint)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", "same-key")
                                .content("{\"slotId\":73001,\"repair\":true}")
                                .with(admin))
                .andExpect(status().isConflict());
    }

    private StockReconciliationService.Report inspect(boolean repair) {
        return reconciliation.reconcileOne(
                SLOT_ID, 9001, UUID.randomUUID().toString(), repair, UUID.randomUUID().toString());
    }

    private String persisted(long user) {
        String id = reserve(user);
        request(id, user, "PERSISTED");
        long orderId = user + 80000;
        db.update(
                "INSERT INTO appointment_order (id,reservation_id,user_id,slot_id,outlet_id,item_id,"
                        + "service_date,slot_start_time,slot_end_time,outlet_name_snapshot,item_name_snapshot,"
                        + "status,confirm_deadline,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                orderId,
                id,
                user,
                SLOT_ID,
                73003,
                ITEM_ID,
                DATE,
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                "outlet",
                "item",
                "CONFIRMED",
                CLOSE,
                NOW.minusSeconds(600),
                NOW.minusSeconds(600));
        db.update(
                "INSERT INTO active_booking_guard (id,user_id,item_id,service_date,"
                        + "reservation_id,appointment_id) VALUES (?,?,?,?,?,?)",
                orderId + 100000,
                user,
                ITEM_ID,
                DATE,
                id,
                orderId);
        assertEquals(
                0,
                stocks.markPersisted(slot, user, id, orderId, NOW, CLOSE.plusSeconds(86400))
                        .code());
        return id;
    }

    private String reserve(long user) {
        String id = UUID.randomUUID().toString();
        assertEquals(
                0,
                stocks.reserve(slot, user, id, NOW, NOW.plusSeconds(600), CLOSE.plusSeconds(86400))
                        .code());
        return id;
    }

    private void request(String id, long user, String status) {
        byte[] key = new byte[32];
        key[0] = (byte) user;
        db.update(
                "INSERT INTO appointment_reservation_request (id,reservation_id,user_id,"
                        + "idempotency_key_hash,payload_hash,slot_id,outlet_id,item_id,service_date,"
                        + "slot_start_time,slot_end_time,outlet_name_snapshot,item_name_snapshot,total_quota,"
                        + "release_at,close_at,slot_status,slot_config_version,status,trace_id,reserved_at,"
                        + "next_recovery_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                user + 70000,
                id,
                user,
                key,
                new byte[32],
                SLOT_ID,
                73003,
                ITEM_ID,
                DATE,
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                "outlet",
                "item",
                5,
                RELEASE,
                CLOSE,
                "OPEN",
                3,
                status,
                "test-trace",
                NOW.minusSeconds(600),
                NOW,
                NOW.minusSeconds(600),
                NOW.minusSeconds(600));
    }

    private void release(String id, long user, String status) {
        db.update(
                "INSERT INTO stock_release_record (id,reservation_id,slot_id,user_id,"
                        + "reason,status,next_attempt_at,released_at,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                user + 90000,
                id,
                SLOT_ID,
                user,
                "PUBLISH_FAILED",
                status,
                NOW,
                "SUCCEEDED".equals(status) ? NOW : null,
                NOW.minusSeconds(600),
                NOW.minusSeconds(600));
    }
}
