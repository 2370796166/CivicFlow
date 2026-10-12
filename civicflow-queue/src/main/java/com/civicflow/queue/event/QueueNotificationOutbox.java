package com.civicflow.queue.event;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.civicflow.queue.entity.OutboxEventEntity;
import com.civicflow.queue.entity.QueueTicketEntity;
import com.civicflow.queue.enums.OutboxStatus;
import com.civicflow.queue.mapper.QueueNotificationOutboxMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class QueueNotificationOutbox {
    public static final String EXCHANGE = "cf.queue.x";
    private final QueueNotificationOutboxMapper outbox;
    private final ObjectMapper json;

    public QueueNotificationOutbox(QueueNotificationOutboxMapper outbox, ObjectMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    public void emit(QueueTicketEntity ticket, String kind, String traceId) {
        String template =
                switch (kind) {
                    case "near" -> "QUEUE_NEAR";
                    case "called" -> "QUEUE_CALLED";
                    case "missed" -> "QUEUE_MISSED";
                    default -> throw new IllegalArgumentException("Unknown queue notification");
                };
        String eventId =
                "near".equals(kind)
                        ? UUID.nameUUIDFromBytes(
                                        ("queue-near:" + ticket.getId())
                                                .getBytes(StandardCharsets.UTF_8))
                                .toString()
                        : UUID.randomUUID().toString();
        Instant now = Instant.now();
        Map<String, Object> event =
                Map.of(
                        "schemaVersion",
                        1,
                        "eventId",
                        eventId,
                        "eventType",
                        "queue.notification." + kind,
                        "eventVersion",
                        1,
                        "occurredAt",
                        now,
                        "producer",
                        "civicflow-queue",
                        "traceId",
                        traceId == null || traceId.isBlank() ? eventId : traceId,
                        "correlationId",
                        ticket.getId().toString(),
                        "causationId",
                        ticket.getAppointmentId().toString(),
                        "payload",
                        Map.of(
                                "userId", ticket.getUserId().toString(),
                                "aggregateId", ticket.getId().toString(),
                                "templateCode", template,
                                "templateVersion", 1,
                                "variables", Map.of("ticketNo", ticket.getTicketNo()),
                                "required", !"missed".equals(kind)));
        OutboxEventEntity entry = new OutboxEventEntity();
        entry.setId(IdWorker.getId());
        entry.setEventId(eventId);
        entry.setAggregateType("queue_ticket");
        entry.setAggregateId(ticket.getId().toString());
        entry.setEventType("queue.notification." + kind);
        entry.setEventVersion(1);
        entry.setRoutingKey("queue.notification." + kind + ".v1");
        try {
            entry.setPayloadJson(json.writeValueAsString(event));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize queue notification", exception);
        }
        entry.setStatus(OutboxStatus.PENDING);
        entry.setAttempts(0);
        entry.setNextAttemptAt(now);
        outbox.insertIgnore(entry);
    }
}
