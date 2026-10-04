package com.civicflow.appointment.event;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record ReservationRequestedEvent(
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

    public static final int SCHEMA_VERSION = 1;
    public static final int EVENT_VERSION = 1;
    public static final String EVENT_TYPE = "appointment.reservation.requested";
    public static final String PRODUCER = "civicflow-appointment";

    public record Payload(
            String reservationId,
            String userId,
            String slotId,
            String outletId,
            String itemId,
            String outletName,
            String itemName,
            LocalDate serviceDate,
            LocalTime slotStartTime,
            LocalTime slotEndTime,
            int totalQuota,
            Instant releaseAt,
            Instant closeAt,
            String slotStatus,
            long slotConfigVersion,
            Instant reservedAt,
            Instant reservationExpiresAt) {}
}
