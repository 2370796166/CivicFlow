package com.civicflow.notification.event;

import java.time.Instant;
import java.util.Map;

public record NotificationEvent(
        int schemaVersion,
        String eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String producer,
        String traceId,
        String correlationId,
        String causationId,
        Payload payload) {
    public record Payload(
            String userId,
            String aggregateId,
            String templateCode,
            int templateVersion,
            Map<String, String> variables,
            boolean required) {}
}
