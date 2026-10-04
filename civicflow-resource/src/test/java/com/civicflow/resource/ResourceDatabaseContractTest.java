package com.civicflow.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.civicflow.resource.entity.ResourceAdminAuditEntity;
import com.civicflow.resource.entity.ResourceAdminIdempotencyEntity;
import com.civicflow.resource.entity.ResourceSlotBatchResultEntity;
import com.civicflow.resource.entity.ResourceSlotDayLockEntity;
import com.civicflow.resource.entity.ResourceSlotEntity;
import com.civicflow.resource.entity.ResourceSlotOutboxEntity;
import com.civicflow.resource.entity.ServiceItemEntity;
import com.civicflow.resource.entity.ServiceOutletEntity;
import com.civicflow.resource.entity.ServiceWindowEntity;
import com.civicflow.resource.entity.StaffWindowScopeEntity;
import com.civicflow.resource.entity.WindowItemRelEntity;
import com.civicflow.resource.error.ResourceErrorCode;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ResourceDatabaseContractTest {
    private static final String DDL = migration("V1__create_resource_schema.sql");
    private static final String ADMIN_DDL = migration("V2__create_resource_admin_control.sql");
    private static final String SLOT_DDL = migration("V3__create_resource_slot_control.sql");

    @Test
    void entitiesMirrorAllResourceTables() {
        assertEntityMatchesTable(ServiceOutletEntity.class, "service_outlet");
        assertEntityMatchesTable(ServiceItemEntity.class, "service_item");
        assertEntityMatchesTable(ServiceWindowEntity.class, "service_window");
        assertEntityMatchesTable(WindowItemRelEntity.class, "window_item_rel");
        assertEntityMatchesTable(StaffWindowScopeEntity.class, "staff_window_scope");
        assertEntityMatchesTable(ResourceSlotEntity.class, "resource_slot");
        assertEntityMatchesTable(
                ResourceAdminIdempotencyEntity.class, "resource_admin_idempotency", ADMIN_DDL);
        assertEntityMatchesTable(ResourceAdminAuditEntity.class, "resource_admin_audit", ADMIN_DDL);
        assertEntityMatchesTable(
                ResourceSlotDayLockEntity.class, "resource_slot_day_lock", SLOT_DDL);
        assertEntityMatchesTable(
                ResourceSlotBatchResultEntity.class, "resource_slot_batch_result", SLOT_DDL);
        assertEntityMatchesTable(ResourceSlotOutboxEntity.class, "resource_slot_outbox", SLOT_DDL);
    }

    @Test
    void enforcesConfigurationAndSlotUniqueness() {
        assertTrue(DDL.contains("UNIQUE KEY uk_outlet_code (code, deleted)"));
        assertTrue(DDL.contains("UNIQUE KEY uk_window_item (window_id, item_id, deleted)"));
        assertTrue(DDL.contains("UNIQUE KEY uk_slot_exact"));
        assertTrue(DDL.contains("KEY idx_slot_calendar"));
        assertTrue(DDL.contains("KEY idx_slot_release"));
        assertTrue(SLOT_DDL.contains("UNIQUE KEY uk_slot_day_lock"));
        assertTrue(SLOT_DDL.contains("UNIQUE KEY uk_resource_slot_outbox_event"));
    }

    @Test
    void exposesConfirmedResourceErrorCodes() {
        assertEquals("RESOURCE_409_VERSION_CONFLICT", ResourceErrorCode.VERSION_CONFLICT.code());
        assertEquals("RESOURCE_409_CODE_EXISTS", ResourceErrorCode.CODE_EXISTS.code());
        assertEquals("RESOURCE_409_SLOT_OVERLAP", ResourceErrorCode.SLOT_OVERLAP.code());
        assertEquals(
                "RESOURCE_409_SLOT_STATE_CONFLICT", ResourceErrorCode.SLOT_STATE_CONFLICT.code());
        assertEquals(
                "RESOURCE_409_QUOTA_BELOW_CONSUMED", ResourceErrorCode.QUOTA_BELOW_CONSUMED.code());
        assertEquals(
                "RESOURCE_409_CONSUMPTION_UNKNOWN", ResourceErrorCode.CONSUMPTION_UNKNOWN.code());
        assertEquals("RESOURCE_409_IN_USE", ResourceErrorCode.IN_USE.code());
        assertTrue(ADMIN_DDL.contains("UNIQUE KEY uk_resource_admin_idempotency"));
        assertTrue(ADMIN_DDL.contains("CREATE TABLE resource_admin_audit"));
    }

    private static void assertEntityMatchesTable(Class<?> entityType, String table) {
        assertEntityMatchesTable(entityType, table, DDL);
    }

    private static void assertEntityMatchesTable(
            Class<?> entityType, String table, String migration) {
        assertEquals(columnsFor(table, migration), fieldsOf(entityType), table);
    }

    private static Set<String> columnsFor(String table, String migration) {
        Matcher tableMatcher =
                Pattern.compile(
                                "(?ms)CREATE TABLE "
                                        + Pattern.quote(table)
                                        + " \\((.*?)^\\) ENGINE")
                        .matcher(migration);
        assertTrue(tableMatcher.find(), table);
        Matcher columnMatcher =
                Pattern.compile(
                                "(?m)^    ([a-z][a-z0-9_]*) (?:BIGINT|INT|SMALLINT|VARCHAR|CHAR|VARBINARY|BINARY|JSON|DECIMAL|DATE|TIME|DATETIME)")
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
                ResourceDatabaseContractTest.class.getResourceAsStream("/db/migration/" + name)) {
            if (input == null) {
                throw new IllegalStateException("Missing migration " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
