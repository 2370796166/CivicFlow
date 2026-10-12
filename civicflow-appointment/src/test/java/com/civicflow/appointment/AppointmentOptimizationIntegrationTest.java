package com.civicflow.appointment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.civicflow.appointment.client.ResourceSlotClient;
import com.civicflow.appointment.service.*;
import com.civicflow.appointment.support.RedisStockRepository;
import com.civicflow.appointment.task.AppointmentFollowupTask;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
class AppointmentOptimizationIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired AppointmentStateService states;
    @Autowired UserMessageService messages;
    @Autowired AppointmentFollowupTask followups;
    @MockitoBean ResourceSlotClient resource;
    @MockitoBean RedisStockRepository stock;

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM user_message");
        jdbc.update("DELETE FROM appointment_active_release");
        jdbc.update("DELETE FROM appointment_operation_log");
        jdbc.update("DELETE FROM active_booking_guard");
        jdbc.update("DELETE FROM appointment_order");
        seed(101, 7, "CONFIRMED");
        seed(102, 8, "CONFIRMED");
    }

    private void seed(long id, long user, String status) {
        jdbc.update(
                "INSERT INTO appointment_order(id,reservation_id,user_id,slot_id,outlet_id,item_id,service_date,slot_start_time,slot_end_time,outlet_name_snapshot,item_name_snapshot,status,confirm_deadline,version) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                id,
                UUID.randomUUID().toString(),
                user,
                11,
                12,
                13,
                LocalDate.now().plusDays(1),
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                "测试网点",
                "测试事项",
                status,
                java.sql.Timestamp.from(Instant.now().plusSeconds(60)));
        jdbc.update(
                "INSERT INTO active_booking_guard(id,user_id,item_id,service_date,reservation_id,appointment_id) SELECT id,user_id,item_id,service_date,reservation_id,id FROM appointment_order WHERE id=?",
                id);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor user(
            long id, String role) {
        return jwt().jwt(j -> j.subject(Long.toString(id)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void administrativeQueriesArePagedAndRoleProtected() throws Exception {
        mvc.perform(get("/api/v1/admin/appointments").with(user(7, "USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/appointments").param("size", "101").with(user(1, "ADMIN")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/appointments").param("userId", "7").with(user(1, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].appointmentId").value("101"));
        mvc.perform(
                        get("/api/v1/admin/appointments/statistics")
                                .param("dateFrom", LocalDate.now().toString())
                                .param("dateTo", LocalDate.now().plusDays(2).toString())
                                .with(user(1, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].count").value(2));
        mvc.perform(
                        get("/api/v1/admin/appointments/statistics")
                                .param("dateFrom", "2027-01-01")
                                .param("dateTo", "2026-01-01")
                                .with(user(1, "ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void messageDeduplicationAndOwnershipCannotBeBypassed() throws Exception {
        messages.send(7, 101, "test-source", "提醒", "查看预约");
        messages.send(7, 101, "test-source", "提醒", "查看预约");
        assertEquals(1, messages.unread(7));
        assertEquals(0, messages.unread(8));
        var id =
                jdbc.queryForObject(
                        "SELECT id FROM user_message WHERE source_key='test-source'", Long.class);
        mvc.perform(post("/api/v1/user/messages/" + id + "/read").with(user(8, "USER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/user/messages").with(user(8, "USER")))
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(post("/api/v1/user/messages/" + id + "/read").with(user(7, "USER")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/user/messages/" + id + "/read").with(user(7, "USER")))
                .andExpect(status().isOk());
        assertEquals(0, messages.unread(7));
    }

    @Test
    void concurrentNoShowCreatesOneTerminalTransitionAndDurableActiveRelease() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            var gate = new CountDownLatch(1);
            var a =
                    pool.submit(
                            () -> {
                                try {
                                    gate.await();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                states.noShow(101, 0, Instant.now().minusSeconds(5), "no-show-1");
                            });
            var b =
                    pool.submit(
                            () -> {
                                try {
                                    gate.await();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                states.noShow(101, 0, Instant.now().minusSeconds(5), "no-show-2");
                            });
            gate.countDown();
            a.get(10, TimeUnit.SECONDS);
            b.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(
                "NO_SHOW",
                jdbc.queryForObject(
                        "SELECT status FROM appointment_order WHERE id=101", String.class));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM appointment_operation_log WHERE appointment_id=101",
                        Integer.class));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM appointment_active_release WHERE appointment_id=101 AND status='PENDING'",
                        Integer.class));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM active_booking_guard WHERE appointment_id=101",
                        Integer.class));
        followups.publishStateMessages();
        followups.publishStateMessages();
        assertEquals(1, messages.unread(7));
    }

    @Test
    void noShowMustNotBeatValidCheckInOrFutureDeadline() {
        states.noShow(101, 0, Instant.now().plusSeconds(60), "future");
        assertEquals(
                "CONFIRMED",
                jdbc.queryForObject(
                        "SELECT status FROM appointment_order WHERE id=101", String.class));
        jdbc.update("UPDATE appointment_order SET status='CHECKED_IN',version=1 WHERE id=101");
        states.noShow(101, 0, Instant.now().minusSeconds(60), "late");
        assertEquals(
                "CHECKED_IN",
                jdbc.queryForObject(
                        "SELECT status FROM appointment_order WHERE id=101", String.class));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM appointment_active_release WHERE appointment_id=101",
                        Integer.class));
    }

    @Test
    void metricsRequireAdministrator() throws Exception {
        mvc.perform(get("/actuator/prometheus").with(user(7, "USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/actuator/prometheus").with(user(1, "ADMIN"))).andExpect(status().isOk());
    }
}
