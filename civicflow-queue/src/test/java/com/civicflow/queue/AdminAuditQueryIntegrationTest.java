package com.civicflow.queue;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Sql("/workbench-h2.sql")
class AdminAuditQueryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;

    @BeforeEach
    void seed() {
        db.update("DELETE FROM queue_operation_log");
        String[] times = {
            "2026-10-01 15:59:59",
            "2026-10-01 16:00:00",
            "2026-10-02 15:59:59",
            "2026-10-02 16:00:00"
        };
        for (int i = 0; i < times.length; i++)
            db.update(
                    "INSERT INTO queue_operation_log(id,actor_id,ticket_id,operation,actor_type,detail_json,request_id,occurred_at) VALUES (?,101,201,'CALL','STAFF','private-payload',?,?)",
                    i + 1,
                    "request-" + (i + 1),
                    java.sql.Timestamp.from(
                            java.time.Instant.parse(times[i].replace(' ', 'T') + "Z")));
    }

    @Test
    void queryUsesShanghaiDateBoundsAndStablePaging() throws Exception {
        var admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        mvc.perform(
                        get("/api/v1/admin/operation-logs/queue")
                                .with(admin)
                                .param("dateFrom", "2026-10-02")
                                .param("dateTo", "2026-10-02")
                                .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value("3"))
                .andExpect(jsonPath("$.data.items[0].beforeJson").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].detailJson").doesNotExist());
        mvc.perform(
                        get("/api/v1/admin/operation-logs/queue")
                                .with(admin)
                                .param("requestId", "request-2")
                                .param("actorId", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(
                        get("/api/v1/admin/operation-logs/queue")
                                .with(admin)
                                .param("dateFrom", "2026-10-03")
                                .param("dateTo", "2026-10-02"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/operation-logs/queue").with(admin).param("resourceId", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedAndNonAdministratorAccessIsDenied() throws Exception {
        mvc.perform(get("/api/v1/admin/operation-logs/queue")).andExpect(status().isUnauthorized());
        for (String role : new String[] {"USER", "STAFF"})
            mvc.perform(
                            get("/api/v1/admin/operation-logs/queue")
                                    .with(
                                            jwt().authorities(
                                                            new SimpleGrantedAuthority(
                                                                    "ROLE_" + role))))
                    .andExpect(status().isForbidden());
    }
}
