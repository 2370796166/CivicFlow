package com.civicflow.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.civicflow.queue.entity.ActiveWindowSessionGuardEntity;
import com.civicflow.queue.entity.CheckinReconciliationRecordEntity;
import com.civicflow.queue.entity.MessageConsumeRecordEntity;
import com.civicflow.queue.entity.OutboxEventEntity;
import com.civicflow.queue.entity.QueueOperationLogEntity;
import com.civicflow.queue.entity.QueueTicketEntity;
import com.civicflow.queue.entity.WindowWorkSessionEntity;
import com.civicflow.queue.error.QueueErrorCode;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class QueueDatabaseContractTest {
    private static final String DDL = migration("V1__create_queue_schema.sql");

    @Test
    void entitiesMirrorAllQueueTables() {
        assertEntityMatchesTable(QueueTicketEntity.class, "queue_ticket");
        assertEntityMatchesTable(QueueOperationLogEntity.class, "queue_operation_log");
        assertEntityMatchesTable(WindowWorkSessionEntity.class, "window_work_session");
        assertEntityMatchesTable(
                ActiveWindowSessionGuardEntity.class, "active_window_session_guard");
        assertEntityMatchesTable(MessageConsumeRecordEntity.class, "message_consume_record");
        assertEntityMatchesTable(OutboxEventEntity.class, "outbox_event");
        assertEntityMatchesTable(
                CheckinReconciliationRecordEntity.class, "checkin_reconciliation_record");
    }

    @Test
    void enforcesCheckinSessionAndCallingConcurrency() {
        assertTrue(DDL.contains("UNIQUE KEY uk_ticket_appointment (appointment_id)"));
        assertTrue(DDL.contains("UNIQUE KEY uk_ticket_display"));
        assertTrue(DDL.contains("PRIMARY KEY (window_id)"));
        assertTrue(DDL.contains("KEY idx_ticket_next"));
        assertTrue(DDL.contains("UNIQUE KEY uk_message_consumer_idempotency"));
        assertTrue(DDL.contains("UNIQUE KEY uk_outbox_event (event_id)"));
    }

    @Test
    void exposesConfirmedQueueErrorCodes() {
        assertEquals("QUEUE_409_SESSION_ACTIVE", QueueErrorCode.SESSION_ACTIVE.code());
        assertEquals("QUEUE_409_STATE_CONFLICT", QueueErrorCode.STATE_CONFLICT.code());
        assertEquals("QUEUE_422_CHECKIN_WINDOW", QueueErrorCode.CHECKIN_WINDOW.code());
    }

    private static void assertEntityMatchesTable(Class<?> entityType, String table) {
        assertEquals(columnsFor(table), fieldsOf(entityType), table);
    }

    private static Set<String> columnsFor(String table) {
        Matcher tableMatcher =
                Pattern.compile(
                                "(?ms)CREATE TABLE "
                                        + Pattern.quote(table)
                                        + " \\((.*?)^\\) ENGINE")
                        .matcher(DDL);
        assertTrue(tableMatcher.find(), table);
        Matcher columnMatcher =
                Pattern.compile(
                                "(?m)^    ([a-z][a-z0-9_]*) (?:BIGINT|INT|SMALLINT|VARCHAR|CHAR|BINARY|DATE|DATETIME|JSON)")
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
                QueueDatabaseContractTest.class.getResourceAsStream("/db/migration/" + name)) {
            if (input == null) {
                throw new IllegalStateException("Missing migration " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
