package com.civicflow.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.civicflow.appointment.entity.ActiveBookingGuardEntity;
import com.civicflow.appointment.entity.AppointmentCommandIdempotencyEntity;
import com.civicflow.appointment.entity.AppointmentOperationLogEntity;
import com.civicflow.appointment.entity.AppointmentOrderEntity;
import com.civicflow.appointment.entity.AppointmentReservationRequestEntity;
import com.civicflow.appointment.entity.MessageConsumeRecordEntity;
import com.civicflow.appointment.entity.OutboxEventEntity;
import com.civicflow.appointment.entity.StockAdminAuditEntity;
import com.civicflow.appointment.entity.StockReconciliationDetailEntity;
import com.civicflow.appointment.entity.StockReconciliationRunEntity;
import com.civicflow.appointment.entity.StockReleaseRecordEntity;
import com.civicflow.appointment.error.AppointmentErrorCode;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AppointmentDatabaseContractTest {
    private static final String DDL = migration("V1__create_appointment_schema.sql");
    private static final String AUDIT_DDL = migration("V2__create_stock_admin_audit.sql");
    private static final String RESERVATION_DDL = migration("V3__create_reservation_request.sql");
    private static final String COMMAND_DDL =
            migration("V4__create_appointment_command_idempotency.sql");
    private static final String RECONCILIATION_DDL =
            migration("V5__reconciliation_idempotency.sql");

    @Test
    void entitiesMirrorAllAppointmentTables() {
        assertEntityMatchesTable(AppointmentOrderEntity.class, "appointment_order");
        assertEntityMatchesTable(ActiveBookingGuardEntity.class, "active_booking_guard");
        assertEntityMatchesTable(AppointmentOperationLogEntity.class, "appointment_operation_log");
        assertEntityMatchesTable(MessageConsumeRecordEntity.class, "message_consume_record");
        assertEntityMatchesTable(OutboxEventEntity.class, "outbox_event");
        assertEquals(
                columnsFor(COMMAND_DDL, "appointment_command_idempotency"),
                fieldsOf(AppointmentCommandIdempotencyEntity.class));
        assertEntityMatchesTable(StockReleaseRecordEntity.class, "stock_release_record");
        Set<String> runColumns = new HashSet<>(columnsFor("stock_reconciliation_run"));
        runColumns.addAll(Set.of("idempotency_key_hash", "request_hash", "report_json"));
        assertEquals(runColumns, fieldsOf(StockReconciliationRunEntity.class));
        assertEntityMatchesTable(
                StockReconciliationDetailEntity.class, "stock_reconciliation_detail");
    }

    @Test
    void enforcesBookingMessageAndCompensationIdempotency() {
        assertTrue(DDL.contains("UNIQUE KEY uk_appointment_reservation (reservation_id)"));
        assertTrue(DDL.contains("UNIQUE KEY uk_active_user_item_date"));
        assertTrue(DDL.contains("UNIQUE KEY uk_message_consumer_idempotency"));
        assertTrue(DDL.contains("UNIQUE KEY uk_outbox_event (event_id)"));
        assertTrue(DDL.contains("UNIQUE KEY uk_stock_release_reservation (reservation_id)"));
        assertTrue(DDL.contains("KEY idx_appointment_timeout"));
        assertTrue(COMMAND_DDL.contains("UNIQUE KEY uk_appt_command_key"));
        assertTrue(RECONCILIATION_DDL.contains("UNIQUE KEY uk_reconciliation_actor_key"));
    }

    @Test
    void exposesConfirmedAppointmentErrorCodes() {
        assertEquals("APPT_409_DUP_ACTIVE", AppointmentErrorCode.DUP_ACTIVE.code());
        assertEquals("APPT_409_STATE_CONFLICT", AppointmentErrorCode.STATE_CONFLICT.code());
        assertEquals("APPT_410_RESERVATION_GONE", AppointmentErrorCode.RESERVATION_GONE.code());
    }

    @Test
    void stockAdminAuditEntityMatchesAppendOnlyAuditTable() {
        assertEquals(
                columnsFor(AUDIT_DDL, "stock_admin_audit"), fieldsOf(StockAdminAuditEntity.class));
        assertTrue(AUDIT_DDL.contains("KEY idx_stock_admin_audit_request (request_id, id)"));
    }

    @Test
    void reservationRequestPersistsHttpIdempotencyAndRecoveryProjection() {
        assertEquals(
                columnsFor(RESERVATION_DDL, "appointment_reservation_request"),
                fieldsOf(AppointmentReservationRequestEntity.class));
        assertTrue(
                RESERVATION_DDL.contains("UNIQUE KEY uk_reservation_request_id (reservation_id)"));
        assertTrue(
                RESERVATION_DDL.contains(
                        "UNIQUE KEY uk_reservation_request_idempotency (user_id, idempotency_key_hash)"));
    }

    private static void assertEntityMatchesTable(Class<?> entityType, String table) {
        assertEquals(columnsFor(table), fieldsOf(entityType), table);
    }

    private static Set<String> columnsFor(String table) {
        return columnsFor(DDL, table);
    }

    private static Set<String> columnsFor(String ddl, String table) {
        Matcher tableMatcher =
                Pattern.compile(
                                "(?ms)CREATE TABLE "
                                        + Pattern.quote(table)
                                        + " \\((.*?)^\\) ENGINE")
                        .matcher(ddl);
        assertTrue(tableMatcher.find(), table);
        Matcher columnMatcher =
                Pattern.compile(
                                "(?m)^    ([a-z][a-z0-9_]*) (?:BIGINT|INT|SMALLINT|VARCHAR|CHAR|BINARY|VARBINARY|DATE|TIME|DATETIME|JSON|LONGTEXT)")
                        .matcher(tableMatcher.group(1));
        return columnMatcher.results().map(result -> result.group(1)).collect(Collectors.toSet());
    }

    private static Set<String> fieldsOf(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(
                        field ->
                                field.getName()
                                        .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                                        .toLowerCase())
                .collect(Collectors.toSet());
    }

    private static String migration(String name) {
        try (var input =
                AppointmentDatabaseContractTest.class.getResourceAsStream(
                        "/db/migration/" + name)) {
            if (input == null) {
                throw new IllegalStateException("Missing migration " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
