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

    private static org.springframework.test.web.servlet.request.RequestPostProcessor
            appointmentService() {
        return jwt().jwt(jwt -> jwt.claim("serviceName", "civicflow-appointment"))
                .authorities(new SimpleGrantedAuthority("SCOPE_resource.slots.read"));
    }
}
