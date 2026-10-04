package com.civicflow.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.civicflow.appointment.client.ResourceSlotClient;
import com.civicflow.appointment.config.AppointmentProperties;
import com.civicflow.appointment.dto.response.AppointmentResponse;
import com.civicflow.appointment.event.AppointmentMessaging;
import com.civicflow.appointment.event.ConfirmTimeoutEvent;
import com.civicflow.appointment.event.ConfirmTimeoutListener;
import com.civicflow.appointment.event.ConfirmTimeoutPublisher;
import com.civicflow.appointment.service.AppointmentStateService;
import com.civicflow.appointment.service.ReservationRedisStateService;
import com.civicflow.appointment.service.ReservationStockService;
import com.civicflow.appointment.support.Digests;
import com.civicflow.appointment.task.StockReleaseRecoveryTask;
import com.civicflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest
class AppointmentStateIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AppointmentStateService states;
    @Autowired private ConfirmTimeoutListener listener;
    @Autowired private StockReleaseRecoveryTask retry;
    @Autowired private AppointmentProperties properties;
    @Autowired private ObjectMapper json;
    @Autowired private ApplicationContext context;
    @MockitoBean private ResourceSlotClient resource;
    @MockitoBean private ReservationRedisStateService redis;
    @MockitoBean private ReservationStockService stock;
    @MockitoBean private ConfirmTimeoutPublisher timeoutPublisher;

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM appointment_command_idempotency");
        jdbc.update("DELETE FROM outbox_event");
        jdbc.update("DELETE FROM stock_release_record");
        jdbc.update("DELETE FROM appointment_operation_log");
        jdbc.update("DELETE FROM active_booking_guard");
        jdbc.update("DELETE FROM appointment_order");
        jdbc.update("DELETE FROM appointment_reservation_request");
        reset(stock);
        when(stock.compensate(any(), anyLong(), anyString(), anyString()))
                .thenReturn(new ReservationStockService.CompensationResult(false, 4, 3));
    }

    @Test
    void timeoutTopologyUsesFiveMinuteTtlAndDeadLetterRouting() {
        Queue delay = context.getBean("confirmDelayQueue", Queue.class);
        assertEquals(300_000, delay.getArguments().get("x-message-ttl"));
        assertEquals(
                AppointmentMessaging.TIMEOUT_EXCHANGE,
                delay.getArguments().get("x-dead-letter-exchange"));
        assertEquals(
                AppointmentMessaging.TIMEOUT_CHECK_ROUTING_KEY,
                delay.getArguments().get("x-dead-letter-routing-key"));
    }

    @Test
    void confirmAndDuplicateConfirmKeepGuardAndDoNotRelease() {
        seed(101, Instant.now().plusSeconds(60));
        AppointmentResponse confirmed = states.confirm(7, 101, 0, "confirm-a", "trace-1");
        assertEquals("CONFIRMED", confirmed.status());
        assertEquals("CONFIRMED", states.confirm(7, 101, 0, "confirm-a", "trace-2").status());
        assertEquals(1, count("appointment_operation_log"));
        assertEquals(1, count("active_booking_guard"));
        assertEquals(0, count("stock_release_record"));
        states.expire(101, "timeout-late");
        assertEquals("CONFIRMED", status(101));
    }

    @Test
    void cancelAndDuplicateCancelReleaseExactlyOnce() {
        seed(102, Instant.now().plusSeconds(60));
        assertEquals(
                "CANCELLED", states.cancel(7, 102, 0, "change", "cancel-a", "trace-1").status());
        assertEquals(
                "CANCELLED", states.cancel(7, 102, 0, "change", "cancel-a", "trace-2").status());
        states.expire(102, "timeout-late");
        assertEquals(1, count("stock_release_record"));
        assertEquals(0, count("active_booking_guard"));
        assertEquals(1, count("appointment_operation_log"));
        verify(stock, times(1)).compensate(any(), anyLong(), anyString(), anyString());
    }

    @Test
    void duplicateTimeoutMessageCannotReleaseTwice() throws Exception {
        Seed seed = seed(103, Instant.now().minusSeconds(2));
        ConfirmTimeoutEvent event =
                new ConfirmTimeoutEvent(
                        1,
                        UUID.randomUUID().toString(),
                        AppointmentMessaging.TIMEOUT_EVENT_TYPE,
                        1,
                        Instant.now(),
                        "civicflow-appointment",
                        "trace-timeout",
                        seed.reservationId(),
                        seed.reservationId(),
                        "timeout:" + seed.reservationId(),
                        new ConfirmTimeoutEvent.Payload(
                                seed.reservationId(), "103", seed.deadline()));
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(1);
        Message message = new Message(json.writeValueAsBytes(event), props);
        Channel channel = org.mockito.Mockito.mock(Channel.class);
        listener.consume(message, channel);
        listener.consume(message, channel);
        assertEquals("EXPIRED", status(103));
        assertEquals(1, count("stock_release_record"));
        assertEquals(1, count("appointment_operation_log"));
        verify(stock, times(1)).compensate(any(), anyLong(), anyString(), anyString());
    }

    @Test
    void earlyTimeoutMessageIsRescheduledWithoutExpiring() throws Exception {
        Seed seed = seed(110, Instant.now().plusSeconds(60));
        ConfirmTimeoutEvent event =
                new ConfirmTimeoutEvent(
                        1,
                        UUID.randomUUID().toString(),
                        AppointmentMessaging.TIMEOUT_EVENT_TYPE,
                        1,
                        Instant.now(),
                        "civicflow-appointment",
                        "trace-early",
                        seed.reservationId(),
                        seed.reservationId(),
                        "timeout:" + seed.reservationId(),
                        new ConfirmTimeoutEvent.Payload(
                                seed.reservationId(), "110", seed.deadline()));
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(2);
        Channel channel = org.mockito.Mockito.mock(Channel.class);
        listener.consume(new Message(json.writeValueAsBytes(event), props), channel);
        verify(timeoutPublisher).reschedule(any());
        assertEquals("PENDING_CONFIRM", status(110));
        assertEquals(0, count("stock_release_record"));
    }

    @Test
    void cancellationAfterConfirmationDeadlineConvergesToExpired() {
        seed(111, Instant.now().minusSeconds(2));
        assertEquals(
                "APPT_409_CONFIRM_TIMEOUT",
                assertThrows(
                                BusinessException.class,
                                () -> states.confirm(7, 111, 0, "late-confirm", "trace-expired"))
                        .errorCode()
                        .code());
        assertEquals(
                "EXPIRED",
                states.cancel(7, 111, 0, "too late", "cancel-expired", "trace-expired").status());
        assertEquals(1, count("stock_release_record"));
        assertEquals(0, count("active_booking_guard"));
    }

    @Test
    void redisFailureKeepsCancelledOrderAndRecoveryRetries() {
        seed(104, Instant.now().plusSeconds(60));
        doThrow(new IllegalStateException("redis unavailable"))
                .when(stock)
                .compensate(any(), anyLong(), anyString(), anyString());
        states.cancel(7, 104, 0, "change", "cancel-redis", "trace-redis");
        assertEquals("CANCELLED", status(104));
        assertEquals(
                "FAILED",
                jdbc.queryForObject("SELECT status FROM stock_release_record", String.class));
        reset(stock);
        when(stock.compensate(any(), anyLong(), anyString(), anyString()))
                .thenReturn(new ReservationStockService.CompensationResult(false, 4, 3));
        jdbc.update(
                "UPDATE stock_release_record SET next_attempt_at=?",
                Timestamp.from(Instant.now().minusSeconds(1)));
        properties.getRecovery().setEnabled(true);
        try {
            retry.retry();
        } finally {
            properties.getRecovery().setEnabled(false);
        }
        assertEquals(
                "SUCCEEDED",
                jdbc.queryForObject("SELECT status FROM stock_release_record", String.class));
        assertEquals("CANCELLED", status(104));
    }

    @Test
    void invalidTransitionsAndOtherUserAreRejected() {
        seed(105, Instant.now().plusSeconds(60));
        assertThrows(BusinessException.class, () -> states.confirm(8, 105, 0, "foreign", "trace"));
        assertEquals(0, count("appointment_command_idempotency"));
        states.confirm(7, 105, 0, "confirm-b", "trace");
        assertThrows(
                BusinessException.class, () -> states.confirm(7, 105, 1, "confirm-b", "trace"));
        jdbc.update("UPDATE appointment_order SET status='CHECKED_IN' WHERE id=105");
        assertThrows(
                BusinessException.class,
                () -> states.cancel(7, 105, 1, "change", "cancel-b", "trace"));
        assertEquals("CHECKED_IN", status(105));
    }

    @Test
    void confirmedAppointmentCanCancelOnlyBeforeLocalSlotStart() {
        seed(108, Instant.now().plusSeconds(60));
        states.confirm(7, 108, 0, "confirm-cancel", "trace");
        assertEquals(
                "CANCELLED",
                states.cancel(7, 108, 1, "changed", "cancel-confirmed", "trace").status());
        assertEquals(1, count("stock_release_record"));

        seed(109, Instant.now().plusSeconds(60));
        states.confirm(7, 109, 0, "confirm-cutoff", "trace");
        jdbc.update(
                "UPDATE appointment_order SET service_date=? WHERE id=109",
                LocalDate.now().minusDays(1));
        assertThrows(
                BusinessException.class,
                () -> states.cancel(7, 109, 1, "late", "cancel-late", "trace"));
        assertEquals("CONFIRMED", status(109));
    }

    @Test
    void concurrentCancelAndTimeoutHaveSingleTerminalTransition() throws Exception {
        seed(106, Instant.now().plusMillis(150));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> cancel =
                    pool.submit(
                            () -> {
                                await(start);
                                try {
                                    states.cancel(
                                            7, 106, 0, "change", "cancel-race", "trace-cancel");
                                } catch (BusinessException ignored) {
                                    // The timeout winner makes cancellation a conflict.
                                }
                            });
            Future<?> timeout =
                    pool.submit(
                            () -> {
                                await(start);
                                try {
                                    Thread.sleep(170);
                                    states.expire(106, "trace-timeout");
                                } catch (InterruptedException exception) {
                                    Thread.currentThread().interrupt();
                                }
                            });
            start.countDown();
            cancel.get();
            timeout.get();
        } finally {
            pool.shutdownNow();
        }
        assertTrue(status(106).equals("CANCELLED") || status(106).equals("EXPIRED"));
        assertEquals(1, count("stock_release_record"));
        assertEquals(1, count("appointment_operation_log"));
    }

    @Test
    void concurrentConfirmAndTimeoutHaveSingleWinner() throws Exception {
        seed(107, Instant.now().plusMillis(150));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> confirm =
                    pool.submit(
                            () -> {
                                await(start);
                                try {
                                    states.confirm(7, 107, 0, "confirm-race", "trace-confirm");
                                } catch (BusinessException ignored) {
                                    // The deadline or timeout won the conditional update.
                                }
                            });
            Future<?> timeout =
                    pool.submit(
                            () -> {
                                await(start);
                                try {
                                    Thread.sleep(170);
                                    states.expire(107, "trace-timeout");
                                } catch (InterruptedException exception) {
                                    Thread.currentThread().interrupt();
                                }
                            });
            start.countDown();
            confirm.get();
            timeout.get();
        } finally {
            pool.shutdownNow();
        }
        assertTrue(status(107).equals("CONFIRMED") || status(107).equals("EXPIRED"));
        assertEquals(1, count("appointment_operation_log"));
        assertEquals(status(107).equals("EXPIRED") ? 1 : 0, count("stock_release_record"));
    }

    private Seed seed(long id, Instant deadline) {
        deadline = deadline.truncatedTo(ChronoUnit.MILLIS);
        String reservation = UUID.randomUUID().toString();
        LocalDate day = LocalDate.now().plusDays(1);
        jdbc.update(
                "INSERT INTO appointment_reservation_request "
                        + "(id,reservation_id,user_id,idempotency_key_hash,payload_hash,slot_id,outlet_id,item_id,"
                        + "service_date,slot_start_time,slot_end_time,outlet_name_snapshot,item_name_snapshot,"
                        + "total_quota,release_at,close_at,slot_status,slot_config_version,status,trace_id,"
                        + "next_recovery_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id,
                reservation,
                7L,
                Digests.sha256("key-" + id),
                new byte[32],
                11L,
                12L,
                13L,
                day,
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                "outlet",
                "item",
                10,
                Timestamp.from(Instant.now().minusSeconds(60)),
                Timestamp.from(Instant.now().plusSeconds(86_400)),
                "OPEN",
                1L,
                "PERSISTED",
                "trace-create",
                Timestamp.from(Instant.now()));
        jdbc.update(
                "INSERT INTO appointment_order "
                        + "(id,reservation_id,user_id,slot_id,outlet_id,item_id,service_date,slot_start_time,"
                        + "slot_end_time,outlet_name_snapshot,item_name_snapshot,status,confirm_deadline,version)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id,
                reservation,
                7L,
                11L,
                12L,
                13L,
                day,
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                "outlet",
                "item",
                "PENDING_CONFIRM",
                Timestamp.from(deadline),
                0);
        jdbc.update(
                "INSERT INTO active_booking_guard "
                        + "(id,user_id,item_id,service_date,reservation_id,appointment_id) VALUES (?,?,?,?,?,?)",
                id,
                7L,
                13L,
                day,
                reservation,
                id);
        return new Seed(reservation, deadline);
    }

    private String status(long id) {
        return jdbc.queryForObject(
                "SELECT status FROM appointment_order WHERE id=?", String.class, id);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private record Seed(String reservationId, Instant deadline) {}
}
