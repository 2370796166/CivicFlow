package com.civicflow.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.civicflow.resource.entity.ResourceSlotEntity;
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

    @Test
    void entitiesMirrorAllResourceTables() {
        assertEntityMatchesTable(ServiceOutletEntity.class, "service_outlet");
        assertEntityMatchesTable(ServiceItemEntity.class, "service_item");
        assertEntityMatchesTable(ServiceWindowEntity.class, "service_window");
        assertEntityMatchesTable(WindowItemRelEntity.class, "window_item_rel");
        assertEntityMatchesTable(StaffWindowScopeEntity.class, "staff_window_scope");
        assertEntityMatchesTable(ResourceSlotEntity.class, "resource_slot");
    }

    @Test
    void enforcesConfigurationAndSlotUniqueness() {
        assertTrue(DDL.contains("UNIQUE KEY uk_outlet_code (code, deleted)"));
        assertTrue(DDL.contains("UNIQUE KEY uk_window_item (window_id, item_id, deleted)"));
        assertTrue(DDL.contains("UNIQUE KEY uk_slot_exact"));
        assertTrue(DDL.contains("KEY idx_slot_calendar"));
        assertTrue(DDL.contains("KEY idx_slot_release"));
    }

    @Test
    void exposesConfirmedResourceErrorCodes() {
        assertEquals("RESOURCE_409_VERSION_CONFLICT", ResourceErrorCode.VERSION_CONFLICT.code());
        assertEquals("RESOURCE_409_SLOT_OVERLAP", ResourceErrorCode.SLOT_OVERLAP.code());
        assertEquals("RESOURCE_409_IN_USE", ResourceErrorCode.IN_USE.code());
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
                                "(?m)^    ([a-z][a-z0-9_]*) (?:BIGINT|INT|SMALLINT|VARCHAR|VARBINARY|DECIMAL|DATE|TIME|DATETIME)")
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
