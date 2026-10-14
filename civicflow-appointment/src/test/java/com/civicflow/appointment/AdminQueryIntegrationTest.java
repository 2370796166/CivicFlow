package com.civicflow.appointment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.civicflow.appointment.client.ResourceSlotClient;
import com.civicflow.appointment.client.ResourceSlotSnapshot;
import com.civicflow.appointment.entity.AppointmentOperationLogEntity;
import com.civicflow.appointment.entity.AppointmentOrderEntity;
import com.civicflow.appointment.entity.StockReconciliationRunEntity;
import com.civicflow.appointment.enums.*;
import com.civicflow.appointment.mapper.*;
import com.civicflow.appointment.service.StockReconciliationService;
import com.civicflow.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class AdminQueryIntegrationTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.civicflow.appointment.service.AdminAuthorizationService adminAuthorization;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired AppointmentOrderMapper orders;
    @Autowired AppointmentOperationLogMapper logs;
    @Autowired StockReconciliationRunMapper runs;
    @Autowired ObjectMapper json;
    @MockitoBean ResourceSlotClient resource;

    @BeforeEach
    void seed() {
        db.update("DELETE FROM appointment_operation_log");
        db.update("DELETE FROM appointment_order");
        db.update("DELETE FROM stock_reconciliation_detail");
        db.update("DELETE FROM stock_reconciliation_run");
        for (int i = 1; i <= 3; i++) {
            var row = new AppointmentOrderEntity();
            row.setId(2104878377833562100L + i);
            row.setReservationId("reservation-" + i);
            row.setUserId(i == 3 ? 102L : 101L);
            row.setSlotId(201L);
            row.setOutletId(301L);
            row.setItemId(401L);
            row.setServiceDate(LocalDate.of(2026, 10, i));
            row.setSlotStartTime(LocalTime.of(9, 0));
            row.setSlotEndTime(LocalTime.of(10, 0));
            row.setOutletNameSnapshot("测试网点");
            row.setItemNameSnapshot("测试事项");
            row.setStatus(AppointmentStatus.PENDING_CONFIRM);
            row.setConfirmDeadline(Instant.parse("2026-01-01T00:00:00Z"));
            row.setVersion(0);
            row.setQrNonceHash(new byte[32]);
            row.setQrKeyId("private-key-marker");
            row.setCancelReason("private-payload");
            orders.insert(row);
            var log = new AppointmentOperationLogEntity();
            log.setId((long) i);
            log.setAppointmentId(row.getId());
            log.setReservationId(row.getReservationId());
            log.setActorType(AppointmentActorType.USER);
            log.setActorId(row.getUserId());
            log.setOperation(AppointmentOperation.CREATE);
            log.setToStatus(AppointmentStatus.PENDING_CONFIRM);
            log.setRequestId("request-" + i);
            log.setOccurredAt(Instant.parse("2026-10-0" + i + "T01:00:00Z"));
            log.setDetailJson("{\"secret\":\"private-payload\"}");
            logs.insert(log);
        }
    }

    @Test
    void adminCanPageFilterAndReadAllUsersWithoutExposingSecretsOrChangingState() throws Exception {
        var admin =
                jwt().jwt(j -> j.subject("9001"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        mvc.perform(
                        get("/api/v1/admin/appointments")
                                .with(admin)
                                .param("size", "1")
                                .param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(
                        jsonPath("$.data.items[0].appointment.appointmentId")
                                .value("2104878377833562102"));
        mvc.perform(
                        get("/api/v1/admin/appointments")
                                .with(admin)
                                .param("userId", "101")
                                .param("dateFrom", "2026-10-02")
                                .param("status", "PENDING_CONFIRM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/v1/admin/appointments/2104878377833562101").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("101"))
                .andExpect(jsonPath("$.data.appointment.qrNonceHash").doesNotExist())
                .andExpect(jsonPath("$.data.appointment.cancelReason").doesNotExist());
        assertEquals(
                "PENDING_CONFIRM",
                db.queryForObject(
                        "SELECT status FROM appointment_order WHERE id=2104878377833562101",
                        String.class));
        mvc.perform(
                        get("/api/v1/admin/appointments")
                                .with(admin)
                                .param("keyword", "2104878377833562103"))
                .andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/v1/admin/appointments").with(admin).param("size", "101"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        get("/api/v1/admin/appointments")
                                .with(admin)
                                .param("dateFrom", "2026-10-03")
                                .param("dateTo", "2026-10-01"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/appointments").with(admin).param("status", "INVALID"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void allQueriesRequireAdminAndLogsArePublicProjections() throws Exception {
        String[] paths = {
            "/api/v1/admin/appointments",
            "/api/v1/admin/reconciliations",
            "/api/v1/admin/operation-logs/appointment",
            "/api/v1/admin/operation-logs/stock"
        };
        for (String path : paths) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                    .andExpect(status().isForbidden());
            mvc.perform(get(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(
                        get("/api/v1/admin/operation-logs/appointment")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                                .param("resourceId", "2104878377833562102")
                                .param("dateFrom", "2026-10-02")
                                .param("dateTo", "2026-10-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].action").value("CREATE"))
                .andExpect(jsonPath("$.data.items[0].detailJson").doesNotExist());
    }

    @Test
    void historicalReportsSurviveReadsAndExpiredRerunsHaveSpecificErrors() throws Exception {
        var report =
                new StockReconciliationService.Report(
                        "901",
                        "201",
                        8,
                        8L,
                        0L,
                        10,
                        2,
                        0,
                        0,
                        0,
                        ReconciliationClassification.CONSISTENT,
                        Instant.parse("2026-10-01T01:00:00Z"),
                        false,
                        ReconciliationRepairStatus.NOT_REQUIRED);
        var run = new StockReconciliationRunEntity();
        run.setId(901L);
        run.setSlotId(201L);
        run.setSlotScopeJson("{\"slotId\":\"201\"}");
        run.setTriggerType(ReconciliationTriggerType.ADMIN);
        run.setStatus(ReconciliationRunStatus.COMPLETED);
        run.setStartedAt(report.detectedAt());
        run.setCompletedAt(report.detectedAt());
        run.setActorId(9001L);
        run.setReportJson(json.writeValueAsString(report));
        runs.insert(run);
        var admin =
                jwt().jwt(j -> j.subject("9001"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        mvc.perform(get("/api/v1/admin/reconciliations").with(admin).param("slotId", "201"))
                .andExpect(jsonPath("$.data.total").value(1));
        for (int i = 0; i < 2; i++)
            mvc.perform(get("/api/v1/admin/reconciliations/901").with(admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.report.classification").value("CONSISTENT"))
                    .andExpect(jsonPath("$.data.idempotencyKeyHash").doesNotExist());
        Instant close = Instant.now().minusSeconds(60), release = close.minusSeconds(3600);
        when(resource.getSnapshot(anyLong()))
                .thenReturn(
                        ApiResponse.success(
                                new ResourceSlotSnapshot(
                                        "201",
                                        "301",
                                        "401",
                                        "网点",
                                        "事项",
                                        LocalDate.of(2026, 9, 29),
                                        LocalTime.of(9, 0),
                                        LocalTime.of(10, 0),
                                        10,
                                        release,
                                        release,
                                        close,
                                        close,
                                        "OPEN",
                                        1),
                                "snapshot"));
        mvc.perform(
                        post("/api/v1/admin/reconciliations")
                                .with(admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", "expired-admin-query-test")
                                .content("{\"slotId\":\"201\",\"repair\":false}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("APPT_422_RECONCILIATION_CLOSED"));
        assertEquals(
                1,
                db.queryForObject(
                        "SELECT COUNT(*) FROM stock_reconciliation_run WHERE slot_id=201 AND status='FAILED' AND failure_code='APPT_422_RECONCILIATION_CLOSED'",
                        Integer.class));
    }
}
