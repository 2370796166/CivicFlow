package com.civicflow.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class AdminUserNamesIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;

    @BeforeEach
    void seed() {
        db.update("DELETE FROM sys_user_role WHERE user_id=900");
        db.update("DELETE FROM sys_user WHERE id IN (900,2106001541063528450,2106001541063528451)");
        db.update(
                "INSERT INTO sys_user(id,username,password_hash,display_name,status) VALUES (900,'names_admin','never-return-this-hash','管理员','ENABLED'),(2106001541063528450,'name_demo','never-return-this-hash','陈嘉明','DISABLED'),(2106001541063528451,'deleted_demo','never-return-this-hash','已删除用户','ENABLED')");
        db.update("UPDATE sys_user SET deleted=id WHERE id=2106001541063528451");
        db.update("INSERT INTO sys_user_role(id,user_id,role_id) VALUES (900,900,3)");
    }

    @Test
    void resolvesExactStringIdsAndOnlyReturnsNameFields() throws Exception {
        mvc.perform(
                        get("/api/v1/admin/users/names")
                                .param(
                                        "ids",
                                        "2106001541063528450,2106001541063528450,2106001541063528451,999")
                                .with(
                                        jwt().jwt(
                                                        token ->
                                                                token.subject("900")
                                                                        .claim("tokenVersion", 0))
                                                .authorities(
                                                        new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("2106001541063528450"))
                .andExpect(jsonPath("$.data[0].displayName").value("陈嘉明"))
                .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data[0].username").doesNotExist())
                .andExpect(jsonPath("$.data[0].mobileCipher").doesNotExist());
    }

    @Test
    void rejectsUnboundedInvalidAndUnauthorizedLookups() throws Exception {
        var admin =
                jwt().jwt(token -> token.subject("900").claim("tokenVersion", 0))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        for (String ids :
                new String[] {
                    "0",
                    "abc",
                    IntStream.rangeClosed(1, 101)
                            .mapToObj(Integer::toString)
                            .collect(Collectors.joining(","))
                })
            mvc.perform(get("/api/v1/admin/users/names").param("ids", ids).with(admin))
                    .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/users/names").with(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/users/names").param("ids", "900"))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        get("/api/v1/admin/users/names")
                                .param("ids", "900")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
        db.update("UPDATE sys_user SET status='DISABLED' WHERE id=900");
        mvc.perform(get("/api/v1/admin/users/names").param("ids", "900").with(admin))
                .andExpect(status().isForbidden());
    }
}
