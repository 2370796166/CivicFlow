package com.civicflow.resource;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(UserSlotQueryIntegrationTest.FixedTime.class)
class UserSlotQueryIntegrationTest {
    @TestConfiguration
    static class FixedTime {
        @Bean
        @Primary
        Clock queryClock() {
            return Clock.fixed(Instant.parse("2026-10-02T14:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach
    void seed() {
        for (String table :
                new String[] {
                    "resource_slot_outbox",
                    "resource_slot_batch_result",
                    "resource_slot_day_lock",
                    "resource_admin_audit",
                    "resource_admin_idempotency",
                    "resource_slot",
                    "staff_window_scope",
                    "window_item_rel",
                    "service_window",
                    "service_item",
                    "service_outlet"
                }) jdbc.update("DELETE FROM " + table);
        jdbc.update(
                "INSERT INTO service_outlet(id,code,name,address,status) VALUES(101,'GZ','广州网点','广州','ENABLED'),(102,'OFF','停用网点','广州','DISABLED')");
        jdbc.update(
                "INSERT INTO service_item(id,code,name,default_duration_minutes,status) VALUES(201,'A','事项',30,'ENABLED'),(202,'B','未绑定事项',30,'ENABLED')");
        jdbc.update(
                "INSERT INTO service_window(id,outlet_id,code,name,status) VALUES(301,101,'W','窗口','ENABLED')");
        jdbc.update("INSERT INTO window_item_rel(id,window_id,item_id) VALUES(401,301,201)");
        slot(
                2106019714059759617L,
                "OPEN",
                "2026-10-02",
                "21:00",
                "23:00",
                "2026-10-02T12:00:00Z",
                0);
        slot(502, "SCHEDULED", "2026-10-02", "22:00", "23:30", "2026-10-02T14:30:00Z", 0);
        slot(503, "SUSPENDED", "2026-10-02", "22:15", "23:45", "2026-10-02T12:00:00Z", 0);
        slot(504, "CLOSED", "2026-10-02", "22:20", "23:50", "2026-10-02T12:00:00Z", 0);
        slot(505, "OPEN", "2026-10-02", "20:00", "21:00", "2026-10-02T12:00:00Z", 0);
        slot(506, "DRAFT", "2026-10-02", "09:00", "10:00", "2026-10-02T01:00:00Z", 0);
        slot(507, "OPEN", "2026-10-02", "10:00", "11:00", "2026-10-02T01:00:00Z", 507);
        slot(508, "OPEN", "2026-10-03", "09:00", "10:00", "2026-10-02T01:00:00Z", 0);
    }

    private void slot(
            long id,
            String state,
            String date,
            String start,
            String end,
            String release,
            long deleted) {
        jdbc.update(
                "INSERT INTO resource_slot(id,outlet_id,item_id,service_date,start_time,end_time,total_quota,release_at,check_in_start,check_in_end,status,config_version,version,created_by,updated_by,deleted) VALUES(?,101,201,?,?,?,?,?,?,?, ?,1,0,1,1,?)",
                id,
                date,
                start,
                end,
                20,
                Instant.parse(release),
                Instant.parse(release),
                Instant.parse(release).plusSeconds(36000),
                state,
                deleted);
    }

    private MockHttpServletRequestBuilder query() {
        return get("/api/v1/user/slots")
                .param("outletId", "101")
                .param("itemId", "201")
                .param("serviceDate", "2026-10-02")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    void filtersPrivateRowsAndComputesShanghaiAvailability() throws Exception {
        mvc.perform(query())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(5))
                .andExpect(jsonPath("$.data.items[0].availability").value("ENDED"))
                .andExpect(jsonPath("$.data.items[0].reservable").value(false))
                .andExpect(jsonPath("$.data.items[1].id").value("2106019714059759617"))
                .andExpect(jsonPath("$.data.items[1].availability").value("OPEN"))
                .andExpect(jsonPath("$.data.items[1].reservable").value(true))
                .andExpect(jsonPath("$.data.items[1].configVersion").doesNotExist())
                .andExpect(jsonPath("$.data.items[2].availability").value("NOT_RELEASED"))
                .andExpect(jsonPath("$.data.items[3].availability").value("SUSPENDED"))
                .andExpect(jsonPath("$.data.items[4].availability").value("CLOSED"));
        mvc.perform(query().param("page", "2").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value("502"))
                .andExpect(jsonPath("$.data.items.length()").value(2));
    }

    @Test
    void rejectsUnavailableResourcesAndInvalidFilters() throws Exception {
        mvc.perform(
                        query().with(
                                        request -> {
                                            request.setParameter("itemId", "202");
                                            return request;
                                        }))
                .andExpect(status().isNotFound());
        mvc.perform(
                        query().with(
                                        request -> {
                                            request.setParameter("outletId", "102");
                                            return request;
                                        }))
                .andExpect(status().isNotFound());
        jdbc.update("UPDATE service_item SET status='DISABLED' WHERE id=201");
        // Preserve the published list contract: disabled items have no public slots.
        mvc.perform(query())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(
                        query().with(
                                        request -> {
                                            request.setParameter("serviceDate", "invalid");
                                            return request;
                                        }))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        query().with(
                                        request -> {
                                            request.setParameter("page", "0");
                                            return request;
                                        }))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        query().with(
                                        request -> {
                                            request.setParameter("size", "101");
                                            return request;
                                        }))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        get("/api/v1/user/slots")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresUserAndKeepsAdminWritesProtected() throws Exception {
        mvc.perform(get("/api/v1/user/slots")).andExpect(status().isUnauthorized());
        mvc.perform(query().with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get("/api/v1/admin/slots")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void scheduledSlotsBecomeReservableAtReleaseTime() throws Exception {
        jdbc.update(
                "UPDATE resource_slot SET release_at=? WHERE id=502",
                Instant.parse("2026-10-02T14:00:00Z"));
        mvc.perform(query())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[2].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.items[2].availability").value("OPEN"))
                .andExpect(jsonPath("$.data.items[2].reservable").value(true));
    }
}
