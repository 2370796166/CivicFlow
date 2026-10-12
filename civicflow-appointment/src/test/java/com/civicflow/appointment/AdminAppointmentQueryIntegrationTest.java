package com.civicflow.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.civicflow.appointment.client.ResourceSlotClient;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class AdminAppointmentQueryIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private ResourceSlotClient resource;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM appointment_operation_log");
        jdbc.update("DELETE FROM appointment_order");
        for (long id : List.of(9001L, 9002L)) {
            String reservation = "10000000-0000-0000-0000-00000000" + id;
            jdbc.update(
                    "INSERT INTO appointment_order (id,reservation_id,user_id,slot_id,outlet_id,item_id,service_date,slot_start_time,slot_end_time,outlet_name_snapshot,item_name_snapshot,status,confirm_deadline,qr_nonce_hash,qr_key_id) "
                            + "VALUES (?,?,?,5001,3001,4001,?,?,?,'Hall','Permit','CONFIRMED',CURRENT_TIMESTAMP,?,?)",
                    id,
                    reservation,
                    id == 9001 ? 7001L : 7002L,
                    LocalDate.of(2030, 1, 1),
                    LocalTime.of(9, 0),
                    LocalTime.of(9, 30),
                    new byte[32],
                    "PRIVATE_KEY_ID");
        }
        jdbc.update(
                "INSERT INTO appointment_operation_log (id,appointment_id,reservation_id,actor_type,actor_id,operation,from_status,to_status,request_id,occurred_at,detail_json) "
                        + "VALUES (9101,9001,'10000000-0000-0000-0000-000000009001','USER',7001,'CONFIRM','PENDING_CONFIRM','CONFIRMED','audit-request',CURRENT_TIMESTAMP,'{\"privateToken\":\"DO_NOT_EXPOSE\"}')");
    }

    @Test
    void adminQueriesArePagedFilteredAndNeverExposeQrCredentials() throws Exception {
        mvc.perform(get("/api/v1/admin/appointments").with(role("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
        mvc.perform(
                        get("/api/v1/admin/appointments")
                                .with(role("ADMIN"))
                                .param("size", "1")
                                .param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].appointment.appointmentId").value("9001"));
        String body =
                mvc.perform(
                                get("/api/v1/admin/appointments")
                                        .with(role("ADMIN"))
                                        .param("userId", "7001")
                                        .param("status", "CONFIRMED")
                                        .param("serviceDate", "2030-01-01"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.total").value(1))
                        .andExpect(jsonPath("$.data.items[0].userId").value("7001"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertFalse(body.contains("PRIVATE_KEY_ID"));
        assertFalse(body.contains("qrNonceHash"));
        mvc.perform(get("/api/v1/admin/appointments/9001").with(role("ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/appointments/9999").with(role("ADMIN")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/appointments").with(role("ADMIN")).param("size", "101"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/appointments").with(role("ADMIN")).param("userId", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        get("/api/v1/admin/appointments")
                                .with(role("ADMIN"))
                                .param("status", "INVALID"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void auditQueriesAreReadOnlyAndRestrictedToAdmins() throws Exception {
        String body =
                mvc.perform(
                                get("/api/v1/admin/appointment-operation-logs")
                                        .with(role("ADMIN"))
                                        .param("appointmentId", "9001")
                                        .param("operation", "CONFIRM"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.total").value(1))
                        .andExpect(jsonPath("$.data.items[0].actorId").value("7001"))
                        .andExpect(jsonPath("$.data.items[0].toStatus").value("CONFIRMED"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertFalse(body.contains("DO_NOT_EXPOSE"));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM appointment_operation_log", Integer.class));
        for (String deniedRole : List.of("USER", "STAFF")) {
            mvc.perform(get("/api/v1/admin/appointments").with(role(deniedRole)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/admin/appointments/9001").with(role(deniedRole)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/admin/appointment-operation-logs").with(role(deniedRole)))
                    .andExpect(status().isForbidden());
        }
    }

    private static RequestPostProcessor role(String role) {
        return jwt().jwt(jwt -> jwt.subject("1").claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
