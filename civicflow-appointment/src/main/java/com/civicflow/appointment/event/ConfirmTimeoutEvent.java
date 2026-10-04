package com.civicflow.appointment.event;

import java.time.Instant;

public record ConfirmTimeoutEvent(
        int schemaVersion,
        String eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String producer,
        String traceId,
        String correlationId,
        String causationId,
        String idempotencyKey,
        Payload payload) {
    public record Payload(String reservationId, String appointmentId, Instant confirmDeadline) {}
}
