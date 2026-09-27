package com.civicflow.resource;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
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
class ResourceInternalSlotIntegrationTest {
    private static final long SLOT_ID = 99001;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;

    @BeforeEach
    void seedSlot() {
        jdbcTemplate.update("DELETE FROM staff_window_scope");
        jdbcTemplate.update("DELETE FROM window_item_rel");
        jdbcTemplate.update("DELETE FROM service_window");
        jdbcTemplate.update("DELETE FROM resource_slot");
        jdbcTemplate.update("DELETE FROM service_item");
        jdbcTemplate.update("DELETE FROM service_outlet");
        jdbcTemplate.update(
                """
                INSERT INTO service_outlet
                  (id,code,name,address,status,version,deleted)
                VALUES (101,'OUTLET-101','东城政务中心','测试地址','ENABLED',0,0)
                """);
        jdbcTemplate.update(
                """
                INSERT INTO service_item
                  (id,code,name,default_duration_minutes,status,version,deleted)
                VALUES (201,'ITEM-201','户籍服务',30,'ENABLED',0,0)
                """);
        jdbcTemplate.update(
                """
                INSERT INTO resource_slot
                  (id,outlet_id,item_id,service_date,start_time,end_time,total_quota,
                   release_at,check_in_start,check_in_end,status,config_version,version,
                   created_by,updated_by,deleted)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                SLOT_ID,
                101,
                201,
                LocalDate.of(2026, 9, 20),
                "09:00:00",
                "10:00:00",
                8,
                Instant.parse("2026-09-16T03:00:00Z"),
                Instant.parse("2026-09-20T00:30:00Z"),
                Instant.parse("2026-09-20T02:00:00Z"),
                "SCHEDULED",
                3,
                0,
                1,
                1,
                0);
    }

    @Test
    void appointmentServiceCanReadSnapshotAndCandidates() throws Exception {
        mockMvc.perform(
                        get("/internal/v1/resource/slots/{slotId}/snapshot", SLOT_ID)
                                .with(appointmentService()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slotId").value(Long.toString(SLOT_ID)))
                .andExpect(jsonPath("$.data.totalQuota").value(8))
                .andExpect(jsonPath("$.data.outletName").value("东城政务中心"))
                .andExpect(jsonPath("$.data.itemName").value("户籍服务"))
                .andExpect(jsonPath("$.data.configVersion").value(3));

        mockMvc.perform(
                        get("/internal/v1/resource/slots/preheat-candidates")
                                .param("releaseFrom", "2026-09-16T02:00:00Z")
                                .param("releaseTo", "2026-09-16T04:00:00Z")
                                .param("afterId", "0")
                                .param("size", "10")
                                .with(appointmentService()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].slotId").value(Long.toString(SLOT_ID)))
                .andExpect(jsonPath("$.data.hasMore").value(false));
    }

    @Test
    void rejectsServiceWithoutRequiredIdentity() throws Exception {
        mockMvc.perform(
                        get("/internal/v1/resource/slots/{slotId}/snapshot", SLOT_ID)
                                .with(
                                        jwt().jwt(
                                                        jwt ->
                                                                jwt.claim(
                                                                        "serviceName",
                                                                        "civicflow-queue"))
                                                .authorities(
                                                        new SimpleGrantedAuthority(
                                                                "SCOPE_resource.slots.read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffAuthorizationRequiresQueueServiceIdentity() throws Exception {
        var call =
                get("/internal/v1/resource/staff/authorization")
                        .param("staffUserId", "7001")
                        .param("outletId", "101");
        mockMvc.perform(call.with(appointmentService())).andExpect(status().isForbidden());
        mockMvc.perform(
                        get("/internal/v1/resource/staff/authorization")
                                .param("staffUserId", "7001")
                                .param("outletId", "101")
                                .with(
                                        jwt().jwt(
                                                        jwt ->
                                                                jwt.claim(
                                                                        "serviceName",
                                                                        "civicflow-queue"))
                                                .authorities(
                                                        new SimpleGrantedAuthority(
                                                                "SCOPE_resource.windows.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    void queueServiceReadsOnlyAuthorizedActiveWindowScopes() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO service_window(id,outlet_id,code,name,status,version,deleted) VALUES(301,101,'W301','一号窗口','ENABLED',0,0)");
        jdbcTemplate.update(
                "INSERT INTO window_item_rel(id,window_id,item_id,deleted) VALUES(401,301,201,0)");
        jdbcTemplate.update(
                "INSERT INTO staff_window_scope(id,staff_user_id,outlet_id,window_id,deleted) VALUES(501,7001,101,301,0)");
        var path = "/internal/v1/resource/staff/{staffUserId}/scopes";
        mockMvc.perform(get(path, 7001).with(appointmentService()))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        get(path, 7001)
                                .with(
                                        jwt().jwt(
                                                        token ->
                                                                token.claim(
                                                                        "serviceName",
                                                                        "civicflow-queue"))
                                                .authorities(
                                                        new SimpleGrantedAuthority(
                                                                "SCOPE_resource.windows.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].outletId").value("101"))
                .andExpect(jsonPath("$.data[0].windowId").value("301"))
                .andExpect(jsonPath("$.data[0].items[0].id").value("201"));
        jdbcTemplate.update("UPDATE service_window SET status='DISABLED' WHERE id=301");
        mockMvc.perform(
                        get(path, 7001)
                                .with(
                                        jwt().jwt(
                                                        token ->
                                                                token.claim(
                                                                        "serviceName",
                                                                        "civicflow-queue"))
                                                .authorities(
                                                        new SimpleGrantedAuthority(
                                                                "SCOPE_resource.windows.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor
            appointmentService() {
        return jwt().jwt(jwt -> jwt.claim("serviceName", "civicflow-appointment"))
                .authorities(new SimpleGrantedAuthority("SCOPE_resource.slots.read"));
    }
}
