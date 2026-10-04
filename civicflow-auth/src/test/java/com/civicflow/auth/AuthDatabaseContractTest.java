package com.civicflow.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.civicflow.auth.entity.AuthAdminIdempotencyEntity;
import com.civicflow.auth.entity.AuthSecurityAuditEntity;
import com.civicflow.auth.entity.RefreshTokenEntity;
import com.civicflow.auth.entity.SysRoleEntity;
import com.civicflow.auth.entity.SysUserEntity;
import com.civicflow.auth.entity.SysUserRoleEntity;
import com.civicflow.auth.error.AuthErrorCode;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AuthDatabaseContractTest {
    private static final String DDL = migration("V1__create_auth_schema.sql");
    private static final String V2_DDL = migration("V2__create_auth_admin_idempotency.sql");
    private static final String V3_DDL = migration("V3__create_auth_security_audit.sql");

    @Test
    void entitiesMirrorAllAuthTables() {
        assertEntityMatchesTable(SysUserEntity.class, "sys_user");
        assertEntityMatchesTable(SysRoleEntity.class, "sys_role");
        assertEntityMatchesTable(SysUserRoleEntity.class, "sys_user_role");
        assertEntityMatchesTable(RefreshTokenEntity.class, "refresh_token");
        assertEntityMatchesTable(
                AuthAdminIdempotencyEntity.class, "auth_admin_idempotency", V2_DDL);
        assertEntityMatchesTable(AuthSecurityAuditEntity.class, "auth_security_audit", V3_DDL);
    }

    @Test
    void protectsLoginAndRefreshUniquenessWithoutPlaintextSecrets() {
        assertTrue(DDL.contains("UNIQUE KEY uk_user_username (username, deleted)"));
        assertTrue(DDL.contains("UNIQUE KEY uk_user_mobile_hash (mobile_hash, deleted)"));
        assertTrue(DDL.contains("UNIQUE KEY uk_refresh_hash (token_hash)"));
        assertTrue(DDL.contains("mobile_cipher VARBINARY(512)"));
        assertTrue(DDL.contains("mobile_hash BINARY(32)"));
        assertTrue(DDL.contains("token_hash BINARY(32)"));
    }

    @Test
    void exposesConfirmedAuthErrorCodes() {
        assertEquals("AUTH_401_UNAUTHORIZED", AuthErrorCode.UNAUTHORIZED.code());
        assertEquals("AUTH_401_REFRESH_REUSED", AuthErrorCode.REFRESH_REUSED.code());
        assertEquals("AUTH_403_FORBIDDEN", AuthErrorCode.FORBIDDEN.code());
    }

    private static void assertEntityMatchesTable(Class<?> entityType, String table) {
        assertEntityMatchesTable(entityType, table, DDL);
    }

    private static void assertEntityMatchesTable(Class<?> entityType, String table, String ddl) {
        assertEquals(columnsFor(table, ddl), fieldsOf(entityType), table);
    }

    private static Set<String> columnsFor(String table, String ddl) {
        Pattern tablePattern =
                Pattern.compile(
                        "(?ms)CREATE TABLE " + Pattern.quote(table) + " \\((.*?)^\\) ENGINE");
        Matcher tableMatcher = tablePattern.matcher(ddl);
        assertTrue(tableMatcher.find(), table);
        Matcher columnMatcher =
                Pattern.compile(
                                "(?m)^    ([a-z][a-z0-9_]*) (?:BIGINT|INT|SMALLINT|VARCHAR|CHAR|BINARY|VARBINARY|DATETIME|JSON)")
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
                AuthDatabaseContractTest.class.getResourceAsStream("/db/migration/" + name)) {
            if (input == null) {
                throw new IllegalStateException("Missing migration " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
