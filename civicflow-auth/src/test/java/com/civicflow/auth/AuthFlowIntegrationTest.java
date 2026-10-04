package com.civicflow.auth;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.civicflow.auth.config.MobileProtector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class AuthFlowIntegrationTest {
    private static final String PASSWORD = "Correct-Horse-42";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MobileProtector mobileProtector;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.update("DELETE FROM auth_security_audit");
        jdbcTemplate.update("DELETE FROM auth_admin_idempotency");
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM sys_user_role");
        jdbcTemplate.update("DELETE FROM sys_user");
        insertUser(100L, "admin", "13800000000", "Admin", "ENABLED", 3L);
        insertUser(101L, "normal", "13900000000", "Normal", "ENABLED", 1L);
        insertUser(102L, "disabled", "13700000000", "Disabled", "DISABLED", 1L);
    }

    @Test
    void existingShortPasswordCanLogInWithoutWeakeningUserCreation() throws Exception {
        jdbcTemplate.update(
                "UPDATE sys_user SET password_hash = ? WHERE id = 100",
                passwordEncoder.encode("1234"));
        JsonNode loggedIn = login("admin", "1234", 200);
        login("admin", "4321", 401);
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"loginName\":\"admin\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_VALIDATION"));
        mockMvc.perform(
                        post("/api/v1/admin/users")
                                .header(
                                        "Authorization",
                                        bearer(loggedIn.at("/data/accessToken").asText()))
                                .header("Idempotency-Key", "short-password-rejected")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"username\":\"newuser\",\"password\":\"1234\",\"displayName\":\"New User\",\"roles\":[\"USER\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logsInByUsernameAndMobileAndReturnsCurrentUser() throws Exception {
        JsonNode usernameLogin = login("normal", PASSWORD, 200);
        String accessToken = usernameLogin.at("/data/accessToken").asText();
        SignedJWT jwt = SignedJWT.parse(accessToken);
        org.junit.jupiter.api.Assertions.assertEquals("101", jwt.getJWTClaimsSet().getSubject());
        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.List.of("USER"), jwt.getJWTClaimsSet().getStringListClaim("roles"));
        org.junit.jupiter.api.Assertions.assertEquals(
                0L, jwt.getJWTClaimsSet().getLongClaim("tokenVersion"));
        org.junit.jupiter.api.Assertions.assertNull(jwt.getJWTClaimsSet().getClaim("mobile"));
        org.junit.jupiter.api.Assertions.assertNull(jwt.getJWTClaimsSet().getClaim("username"));
        mockMvc.perform(get("/api/v1/user/me").header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("101"))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"));

        login("+8613900000000", PASSWORD, 200);

        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kid").value("test-key"))
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());
    }

    @Test
    void wrongPasswordAndDisabledAccountShareGenericFailure() throws Exception {
        login("normal", "Wrong-Password-42", 401);
        login("missing", "Wrong-Password-42", 401);
        login("disabled", PASSWORD, 401);
    }

    @Test
    void rotatesRefreshTokenAndRevokesFamilyOnReuse() throws Exception {
        JsonNode loggedIn = login("normal", PASSWORD, 200);
        String original = loggedIn.at("/data/refreshToken").asText();

        JsonNode refreshed = refresh(original, 200);
        String replacement = refreshed.at("/data/refreshToken").asText();
        org.junit.jupiter.api.Assertions.assertNotEquals(original, replacement);

        refresh(original, 401);
        refresh(replacement, 401);
        Integer tokenVersion =
                jdbcTemplate.queryForObject(
                        "SELECT token_version FROM sys_user WHERE id = 101", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, tokenVersion);
        Integer reuseAudit =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM auth_security_audit"
                                + " WHERE target_user_id = 101 AND action = 'REFRESH_TOKEN_REUSE'"
                                + " AND outcome = 'BLOCKED'",
                        Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, reuseAudit);
    }

    @Test
    void logoutIsIdempotent() throws Exception {
        JsonNode loggedIn = login("normal", PASSWORD, 200);
        String accessToken = loggedIn.at("/data/accessToken").asText();
        String refreshToken = loggedIn.at("/data/refreshToken").asText();
        String body = objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken));

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(
                            post("/api/v1/auth/logout")
                                    .header("Authorization", bearer(accessToken))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("OK"));
        }
    }

    @Test
    void userCannotCallAdminEndpoint() throws Exception {
        String userAccess = login("normal", PASSWORD, 200).at("/data/accessToken").asText();
        mockMvc.perform(
                        post("/api/v1/admin/users")
                                .header("Authorization", bearer(userAccess))
                                .header("Idempotency-Key", "unauthorized-create")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createUserJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403_FORBIDDEN"));
    }

    @Test
    void adminCreatesAssignsRolesDisablesAndIdempotentlyRepeats() throws Exception {
        String adminAccess = login("admin", PASSWORD, 200).at("/data/accessToken").asText();
        MvcResult created =
                mockMvc.perform(
                                post("/api/v1/admin/users")
                                        .header("Authorization", bearer(adminAccess))
                                        .header("Idempotency-Key", "create-staff-1")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(createUserJson()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.id", notNullValue()))
                        .andExpect(jsonPath("$.data.id", not("")))
                        .andReturn();
        String userId =
                objectMapper
                        .readTree(created.getResponse().getContentAsString())
                        .at("/data/id")
                        .asText();

        mockMvc.perform(
                        post("/api/v1/admin/users")
                                .header("Authorization", bearer(adminAccess))
                                .header("Idempotency-Key", "create-staff-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createUserJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(userId));

        mockMvc.perform(
                        get("/api/v1/admin/users")
                                .header("Authorization", bearer(adminAccess))
                                .param("keyword", "newstaff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].maskedMobile").value("136****0000"));

        mockMvc.perform(
                        put("/api/v1/admin/users/{userId}/roles", userId)
                                .header("Authorization", bearer(adminAccess))
                                .header("Idempotency-Key", "roles-staff-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"roles\":[\"STAFF\"],\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("STAFF"))
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(
                        patch("/api/v1/admin/users/{userId}/status", userId)
                                .header("Authorization", bearer(adminAccess))
                                .header("Idempotency-Key", "disable-staff-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"DISABLED\",\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.version").value(2));

        String storedPassword =
                jdbcTemplate.queryForObject(
                        "SELECT password_hash FROM sys_user WHERE id = ?",
                        String.class,
                        Long.parseLong(userId));
        org.junit.jupiter.api.Assertions.assertTrue(storedPassword.startsWith("$2"));
        org.junit.jupiter.api.Assertions.assertNotEquals(PASSWORD, storedPassword);
        Integer auditCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM auth_security_audit WHERE target_user_id = ?",
                        Integer.class,
                        Long.parseLong(userId));
        org.junit.jupiter.api.Assertions.assertEquals(3, auditCount);

        login("newstaff", PASSWORD, 401);
    }

    @Test
    void roleChangeImmediatelyInvalidatesOldAdminTokenForManagementCalls() throws Exception {
        String adminAccess = login("admin", PASSWORD, 200).at("/data/accessToken").asText();
        mockMvc.perform(
                        put("/api/v1/admin/users/100/roles")
                                .header("Authorization", bearer(adminAccess))
                                .header("Idempotency-Key", "self-add-staff")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"roles\":[\"ADMIN\",\"STAFF\"],\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(adminAccess)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403_FORBIDDEN"));
    }

    private JsonNode login(String loginName, String password, int expectedStatus) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        Map.of(
                                                                "loginName",
                                                                loginName,
                                                                "password",
                                                                password))))
                        .andExpect(status().is(expectedStatus))
                        .andExpect(
                                jsonPath("$.code")
                                        .value(
                                                expectedStatus == 200
                                                        ? "OK"
                                                        : "AUTH_401_UNAUTHORIZED"))
                        .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode refresh(String refreshToken, int expectedStatus) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/auth/refresh")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        Map.of("refreshToken", refreshToken))))
                        .andExpect(status().is(expectedStatus))
                        .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void insertUser(
            long id,
            String username,
            String mobile,
            String displayName,
            String status,
            long roleId) {
        String normalized = mobileProtector.normalize(mobile);
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, username, mobile_cipher, mobile_hash, mobile_key_version,"
                        + " password_hash, display_name, status, token_version, version, deleted)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0)",
                id,
                username,
                mobileProtector.encrypt(normalized),
                mobileProtector.hash(normalized),
                mobileProtector.keyVersion(),
                passwordEncoder.encode(PASSWORD),
                displayName,
                status);
        jdbcTemplate.update(
                "INSERT INTO sys_user_role (id, user_id, role_id, deleted) VALUES (?, ?, ?, 0)",
                id * 10 + roleId,
                id,
                roleId);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String createUserJson() {
        return "{\"username\":\"newstaff\",\"mobile\":\"13600000000\","
                + "\"password\":\"Correct-Horse-42\",\"displayName\":\"New Staff\","
                + "\"roles\":[\"USER\"]}";
    }
}
