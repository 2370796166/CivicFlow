package com.civicflow.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.civicflow.appointment.client.ResourceSlotClient;
import com.civicflow.appointment.client.ResourceSlotSnapshot;
import com.civicflow.appointment.entity.AppointmentReservationRequestEntity;
import com.civicflow.appointment.event.PermanentReservationMessageException;
import com.civicflow.appointment.event.ReservationCreateListener;
import com.civicflow.appointment.event.ReservationEventHash;
import com.civicflow.appointment.event.ReservationEventPublisher;
import com.civicflow.appointment.event.ReservationRequestedEvent;
import com.civicflow.appointment.mapper.AppointmentReservationRequestMapper;
import com.civicflow.appointment.service.AppointmentCreationService;
import com.civicflow.appointment.service.ReservationRedisStateService;
import com.civicflow.appointment.service.ReservationStockService;
import com.civicflow.appointment.service.ReservationWorkflowService;
import com.civicflow.common.api.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class AppointmentReservationFlowIntegrationTest {
    private static final long USER_ID = 7001;
    private static final long OTHER_USER_ID = 7002;
    private static final long SLOT_ID = 8101;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ReservationWorkflowService workflowService;
    @Autowired private AppointmentCreationService creationService;
    @Autowired private ReservationCreateListener listener;
    @Autowired private AppointmentReservationRequestMapper requestMapper;

    @MockitoBean private ResourceSlotClient resourceClient;
    @MockitoBean private ReservationStockService stockService;
    @MockitoBean private ReservationEventPublisher publisher;
    @MockitoBean private ReservationRedisStateService redisStateService;

    @BeforeEach
    void resetDatabaseAndDefaults() {
        jdbcTemplate.update("DELETE FROM appointment_command_idempotency");
        jdbcTemplate.update("DELETE FROM outbox_event");
        jdbcTemplate.update("DELETE FROM stock_release_record");
        jdbcTemplate.update("DELETE FROM message_consume_record");
        jdbcTemplate.update("DELETE FROM appointment_operation_log");
        jdbcTemplate.update("DELETE FROM active_booking_guard");
        jdbcTemplate.update("DELETE FROM appointment_order");
        jdbcTemplate.update("DELETE FROM appointment_reservation_request");
        when(resourceClient.getSnapshot(anyLong()))
                .thenAnswer(
                        invocation -> {
                            long slotId = invocation.getArgument(0);
                            return ApiResponse.success(snapshot(slotId), "resource-snapshot");
                        });
        when(stockService.reserve(any(), anyLong(), anyString()))
                .thenAnswer(
                        invocation ->
                                new ReservationStockService.ReservationResult(
                                        invocation.getArgument(2), false, 4, 3));
        when(stockService.compensate(any(), anyLong(), anyString(), anyString()))
                .thenReturn(new ReservationStockService.CompensationResult(false, 5, 3));
        when(publisher.publish(any()))
                .thenReturn(
                        new ReservationEventPublisher.PublishResult(
                                ReservationEventPublisher.Outcome.ACKNOWLEDGED, 1, null));
        when(redisStateService.markPublished(any())).thenReturn(true);
        when(redisStateService.markPersisted(any(), anyLong())).thenReturn(true);
    }

    @Test
    void duplicateHttpRequestReusesReservationAndPublishesOnce() throws Exception {
        String first = reserveViaHttp("same-key", SLOT_ID, USER_ID, 202);
        String second = reserveViaHttp("same-key", SLOT_ID, USER_ID, 202);

        assertEquals(first, second);
        verify(stockService, times(1)).reserve(any(), anyLong(), anyString());
        verify(publisher, times(1)).publish(any());
        mockMvc.perform(
                        get("/api/user/appointments/reservations/{reservationId}", first)
                                .with(user(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CREATING"));
    }

    @Test
    void finalPublishFailureRunsAuditedIdempotentCompensation() throws Exception {
        when(publisher.publish(any()))
                .thenReturn(
                        new ReservationEventPublisher.PublishResult(
                                ReservationEventPublisher.Outcome.FAILED, 3, "PUBLISH_NACK"));

        String reservationId = reserveViaHttp("publish-fails", SLOT_ID, USER_ID, 503);

        verify(stockService, times(1))
                .compensate(
                        any(),
                        anyLong(),
                        org.mockito.ArgumentMatchers.eq(reservationId),
                        anyString());
        assertEquals(
                "SUCCEEDED",
                jdbcTemplate.queryForObject(
                        "SELECT status FROM stock_release_record WHERE reservation_id = ?",
                        String.class,
                        reservationId));
        mockMvc.perform(
                        get("/api/user/appointments/reservations/{reservationId}", reservationId)
                                .with(user(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failureCode").value("APPT_503_PUBLISH_FAILED"));
    }

    @Test
    void unknownConfirmIsNeverReclassifiedAsSafeToCompensate() throws Exception {
        when(publisher.publish(any()))
                .thenReturn(
                        new ReservationEventPublisher.PublishResult(
                                ReservationEventPublisher.Outcome.UNKNOWN,
                                3,
                                "PUBLISH_CONFIRM_TIMEOUT"),
                        new ReservationEventPublisher.PublishResult(
                                ReservationEventPublisher.Outcome.FAILED, 3, "PUBLISH_NACK"));

        String first = reserveViaHttp("publish-unknown", SLOT_ID, USER_ID, 503);
        String second = reserveViaHttp("publish-unknown", SLOT_ID, USER_ID, 503);

        assertEquals(first, second);
        verify(stockService, times(0)).compensate(any(), anyLong(), anyString(), anyString());
        assertEquals(0, count("stock_release_record"));
        mockMvc.perform(
                        get("/api/user/appointments/reservations/{reservationId}", first)
                                .with(user(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CREATING"))
                .andExpect(jsonPath("$.data.failureCode").value("APPT_503_PUBLISH_UNKNOWN"));
    }

    @Test
    void recoveryLeaseHasSingleWinnerAndReusesStoredEvent() {
        when(publisher.publish(any()))
                .thenReturn(
                        new ReservationEventPublisher.PublishResult(
                                ReservationEventPublisher.Outcome.UNKNOWN,
                                1,
                                "PUBLISH_CONFIRM_TIMEOUT"),
                        new ReservationEventPublisher.PublishResult(
                                ReservationEventPublisher.Outcome.ACKNOWLEDGED, 1, null));

        workflowService.create(USER_ID, SLOT_ID, "recover-publish", "trace-recover-publish");
        Long requestId =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM appointment_reservation_request WHERE user_id = ?",
                        Long.class,
                        USER_ID);
        AppointmentReservationRequestEntity request = requestMapper.selectById(requestId);
        request.setNextRecoveryAt(Instant.now().minusSeconds(1));
        request.setRecoveryOwner(null);
        request.setRecoveryLeaseUntil(null);
        requestMapper.updateById(request);

        Instant now = Instant.now();
        List<AppointmentReservationRequestEntity> candidates =
                requestMapper.selectRecoverable(now, 10);
        assertEquals(1, candidates.size());
        assertEquals(
                1, requestMapper.claimRecovery(requestId, "owner-a", now, now.plusSeconds(30)));
        assertEquals(
                0, requestMapper.claimRecovery(requestId, "owner-b", now, now.plusSeconds(30)));

        workflowService.recover(requestId);

        ArgumentCaptor<ReservationRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(ReservationRequestedEvent.class);
        verify(publisher, times(2)).publish(eventCaptor.capture());
        assertEquals(
                eventCaptor.getAllValues().get(0).eventId(),
                eventCaptor.getAllValues().get(1).eventId());
        assertEquals(
                "PUBLISHED",
                jdbcTemplate.queryForObject(
                        "SELECT status FROM appointment_reservation_request WHERE id = ?",
                        String.class,
                        requestId));
    }

    @Test
    void duplicateMqMessageCreatesOnePendingAppointment() {
        ReservationRequestedEvent event = createPublishedReservation("duplicate-message", SLOT_ID);

        AppointmentCreationService.CreationResult first =
                creationService.create(event, ReservationEventHash.compute(event));
        AppointmentCreationService.CreationResult second =
                creationService.create(event, ReservationEventHash.compute(event));

        assertEquals(first.appointmentId(), second.appointmentId());
        assertEquals(1, count("appointment_order"));
        assertEquals(1, count("message_consume_record"));
        assertEquals(1, count("active_booking_guard"));
        assertEquals(1, count("outbox_event"));
        assertEquals(
                "PENDING_CONFIRM",
                jdbcTemplate.queryForObject(
                        "SELECT status FROM appointment_order WHERE reservation_id = ?",
                        String.class,
                        event.eventId()));
    }

    @Test
    void consumerCrashAfterCommitBeforeAckRedeliversIdempotently() throws Exception {
        ReservationRequestedEvent event = createPublishedReservation("crash-redelivery", SLOT_ID);
        Message message = message(event, 41L);
        Channel crashedChannel = org.mockito.Mockito.mock(Channel.class);
        doThrow(new IOException("connection lost")).when(crashedChannel).basicAck(41L, false);

        assertThrows(IOException.class, () -> listener.consume(message, crashedChannel));
        assertEquals(1, count("appointment_order"));

        Channel redeliveryChannel = org.mockito.Mockito.mock(Channel.class);
        listener.consume(message(event, 42L), redeliveryChannel);
        verify(redeliveryChannel).basicAck(42L, false);
        assertEquals(1, count("appointment_order"));
        assertEquals(1, count("message_consume_record"));
    }

    @Test
    void activeGuardUniqueConflictCompensatesAndSurfacesFailure() throws Exception {
        ReservationRequestedEvent first = createPublishedReservation("guard-first", SLOT_ID);
        listener.consume(message(first, 51L), org.mockito.Mockito.mock(Channel.class));
        ReservationRequestedEvent second = createPublishedReservation("guard-second", SLOT_ID + 1);

        assertThrows(
                PermanentReservationMessageException.class,
                () ->
                        listener.consume(
                                message(second, 52L), org.mockito.Mockito.mock(Channel.class)));

        assertEquals(1, count("appointment_order"));
        assertEquals(
                "FAILED",
                jdbcTemplate.queryForObject(
                        "SELECT status FROM appointment_reservation_request WHERE reservation_id = ?",
                        String.class,
                        second.eventId()));
        assertEquals(
                "SUCCEEDED",
                jdbcTemplate.queryForObject(
                        "SELECT status FROM stock_release_record WHERE reservation_id = ?",
                        String.class,
                        second.eventId()));
        verify(stockService, times(1))
                .compensate(
                        any(),
                        anyLong(),
                        org.mockito.ArgumentMatchers.eq(second.eventId()),
                        org.mockito.ArgumentMatchers.eq("DUPLICATE_GUARD_REJECTED"));
    }

    @Test
    void appointmentAndReservationQueriesHideOtherUsersData() throws Exception {
        ReservationRequestedEvent event = createPublishedReservation("owned-query", SLOT_ID);
        listener.consume(message(event, 61L), org.mockito.Mockito.mock(Channel.class));
        String appointmentId =
                jdbcTemplate
                        .queryForObject(
                                "SELECT id FROM appointment_order WHERE reservation_id = ?",
                                Long.class,
                                event.eventId())
                        .toString();

        mockMvc.perform(
                        get("/api/user/appointments/{appointmentId}", appointmentId)
                                .with(user(OTHER_USER_ID)))
                .andExpect(status().isNotFound());
        mockMvc.perform(
                        get("/api/user/appointments/reservations/{reservationId}", event.eventId())
                                .with(user(OTHER_USER_ID)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/user/appointments").with(user(OTHER_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(
                        get("/api/user/appointments/{appointmentId}", appointmentId)
                                .with(user(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_CONFIRM"));
    }

    private ReservationRequestedEvent createPublishedReservation(String key, long slotId) {
        workflowService.create(USER_ID, slotId, key, "trace-" + key);
        ArgumentCaptor<ReservationRequestedEvent> captor =
                ArgumentCaptor.forClass(ReservationRequestedEvent.class);
        verify(publisher, times((int) count("appointment_reservation_request")))
                .publish(captor.capture());
        return captor.getAllValues().get(captor.getAllValues().size() - 1);
    }

    private String reserveViaHttp(String key, long slotId, long userId, int expectedStatus)
            throws Exception {
        String content =
                mockMvc.perform(
                                post("/api/user/appointments/reservations")
                                        .header("Idempotency-Key", key)
                                        .header("X-Request-Id", "request-" + key)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"slotId\":\"" + slotId + "\"}")
                                        .with(user(userId)))
                        .andExpect(status().is(expectedStatus))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode json = objectMapper.readTree(content);
        return json.path("data").path("reservationId").asText();
    }

    private Message message(ReservationRequestedEvent event, long deliveryTag) throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(deliveryTag);
        return new Message(objectMapper.writeValueAsBytes(event), properties);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private static ResourceSlotSnapshot snapshot(long slotId) {
        return new ResourceSlotSnapshot(
                Long.toString(slotId),
                "8201",
                "8301",
                "东城政务中心",
                "户籍服务",
                LocalDate.of(2026, 9, 25),
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                5,
                Instant.parse("2026-09-20T00:00:00Z"),
                Instant.parse("2026-09-25T02:00:00Z"),
                "OPEN",
                3);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor user(
            long userId) {
        return jwt().jwt(token -> token.subject(Long.toString(userId)))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
