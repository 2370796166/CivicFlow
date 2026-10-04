package com.civicflow.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("/schema-h2.sql")
class AppointmentQueueStateIntegrationTest {
    @MockitoBean com.civicflow.appointment.support.RedisStockRepository stock;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach
    void seed() {
        jdbc.update(
                "INSERT INTO appointment_order(id,reservation_id,user_id,slot_id,outlet_id,item_id,service_date,slot_start_time,slot_end_time,outlet_name_snapshot,item_name_snapshot,status,confirm_deadline,queue_ticket_id) VALUES(100,'11111111-1111-1111-1111-111111111111',300,400,500,600,?,'09:00:00','10:00:00','outlet','item','CHECKED_IN',CURRENT_TIMESTAMP,200)",
                LocalDate.now());
        jdbc.update(
                "INSERT INTO active_booking_guard(id,user_id,item_id,service_date,reservation_id,appointment_id) VALUES(700,300,600,?,'11111111-1111-1111-1111-111111111111',100)",
                LocalDate.now());
    }

    @Test
    void rejectsWrongServiceAndScope() throws Exception {
        request("SERVING", queueIdentity("SCOPE_appointments.checkin", "civicflow-queue"))
                .andExpect(status().isForbidden());
        request("SERVING", queueIdentity("SCOPE_appointments.queue-state", "civicflow-resource"))
                .andExpect(status().isForbidden());
        assertEquals("CHECKED_IN", statusInDb());
    }

    @Test
    void servingThenCompletionAndRepeatedDelivery() throws Exception {
        RequestPostProcessor service =
                queueIdentity("SCOPE_appointments.queue-state", "civicflow-queue");
        request("COMPLETED", service).andExpect(status().isConflict());
        request("SERVING", service).andExpect(status().isOk());
        request("SERVING", service).andExpect(status().isOk());
        request("COMPLETED", service).andExpect(status().isOk());
        request("COMPLETED", service).andExpect(status().isOk());
        assertEquals("COMPLETED", statusInDb());
        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM appointment_operation_log WHERE actor_type='SERVICE' AND actor_id=0",
                        Integer.class));
        assertEquals(
                0, jdbc.queryForObject("SELECT COUNT(*) FROM active_booking_guard", Integer.class));
        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM appointment_operation_log", Integer.class));
    }

    @Test
    void committedCompletionRetriesRedisCleanupWithoutRepeatingDatabaseEffects() throws Exception {
        RequestPostProcessor service =
                queueIdentity("SCOPE_appointments.queue-state", "civicflow-queue");
        request("SERVING", service).andExpect(status().isOk());
        doThrow(new org.springframework.data.redis.RedisConnectionFailureException("offline"))
                .doNothing()
                .when(stock)
                .releaseActiveIfOwned(anyLong(), any(), anyLong(), any());
        request("COMPLETED", service).andExpect(status().is5xxServerError());
        assertEquals("COMPLETED", statusInDb());
        request("COMPLETED", service).andExpect(status().isOk());
        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM appointment_operation_log", Integer.class));
    }

    @Test
    void missedIsTerminalAndReleasesGuard() throws Exception {
        RequestPostProcessor service =
                queueIdentity("SCOPE_appointments.queue-state", "civicflow-queue");
        request("MISSED", service).andExpect(status().isOk());
        request("MISSED", service).andExpect(status().isOk());
        request("SERVING", service).andExpect(status().isConflict());
        assertEquals("NO_SHOW", statusInDb());
        assertEquals(
                0, jdbc.queryForObject("SELECT COUNT(*) FROM active_booking_guard", Integer.class));
    }

    private org.springframework.test.web.servlet.ResultActions request(
            String state, RequestPostProcessor identity) throws Exception {
        return mvc.perform(
                post("/internal/v1/appointments/100/queue-state")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"ticketId\":\"200\",\"status\":\""
                                        + state
                                        + "\",\"syncId\":\"900\"}")
                        .with(identity));
    }

    private static RequestPostProcessor queueIdentity(String scope, String serviceName) {
        return jwt().jwt(token -> token.claim("serviceName", serviceName))
                .authorities(new SimpleGrantedAuthority(scope));
    }

    private String statusInDb() {
        return jdbc.queryForObject(
                "SELECT status FROM appointment_order WHERE id=100", String.class);
    }
}
