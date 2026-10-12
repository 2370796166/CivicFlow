package com.civicflow.appointment.event;

import com.civicflow.appointment.entity.AppointmentOrderEntity;
import com.civicflow.appointment.entity.OutboxEventEntity;
import com.civicflow.appointment.enums.OutboxStatus;
import com.civicflow.appointment.mapper.OutboxEventMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AppointmentNotificationOutbox {
    private final OutboxEventMapper outbox;
    private final ObjectMapper json;

    public AppointmentNotificationOutbox(OutboxEventMapper outbox, ObjectMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    public void emit(AppointmentOrderEntity order, String kind, String traceId) {
        String template =
                switch (kind) {
                    case "created" -> "APPOINTMENT_CREATED";
                    case "reminder" -> "CONFIRM_REMINDER";
                    case "confirmed" -> "APPOINTMENT_CONFIRMED";
                    case "cancelled" -> "APPOINTMENT_CANCELLED";
                    default -> throw new IllegalArgumentException("Unknown notification kind");
                };
        String eventId =
                "reminder".equals(kind)
                        ? UUID.nameUUIDFromBytes(
                                        ("appointment-reminder:" + order.getId())
                                                .getBytes(StandardCharsets.UTF_8))
                                .toString()
                        : UUID.randomUUID().toString();
        Instant now = Instant.now();
        Map<String, Object> payload =
                Map.of(
                        "userId",
                        order.getUserId().toString(),
                        "aggregateId",
                        order.getId().toString(),
                        "templateCode",
                        template,
                        "templateVersion",
                        1,
                        "variables",
                        Map.of("appointmentId", order.getId().toString()),
                        "required",
                        true);
        Map<String, Object> event =
                Map.of(
                        "schemaVersion",
                        1,
                        "eventId",
                        eventId,
                        "eventType",
                        "appointment.notification." + kind,
                        "eventVersion",
                        1,
                        "occurredAt",
                        now,
                        "producer",
                        "civicflow-appointment",
                        "traceId",
                        traceId == null || traceId.isBlank() ? eventId : traceId,
                        "correlationId",
                        order.getId().toString(),
                        "causationId",
                        order.getReservationId(),
                        "payload",
                        payload);
        OutboxEventEntity entry = new OutboxEventEntity();
        entry.setEventId(eventId);
        entry.setAggregateType("appointment_order");
        entry.setAggregateId(order.getId().toString());
        entry.setEventType("appointment.notification." + kind);
        entry.setEventVersion(1);
        entry.setRoutingKey("appointment.notification." + kind + ".v1");
        try {
            entry.setPayloadJson(json.writeValueAsString(event));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize notification event", exception);
        }
        entry.setStatus(OutboxStatus.PENDING);
        entry.setAttempts(0);
        entry.setNextAttemptAt(
                "reminder".equals(kind) ? order.getConfirmDeadline().minusSeconds(60) : now);
        outbox.insert(entry);
    }
}
