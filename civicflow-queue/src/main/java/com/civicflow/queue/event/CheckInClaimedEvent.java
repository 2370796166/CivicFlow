package com.civicflow.queue.event;

import java.time.Instant;
import java.time.LocalDate;

public record CheckInClaimedEvent(
        String eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String traceId,
        String producer,
        String idempotencyKey,
        String claimId,
        String appointmentId,
        String userId,
        String outletId,
        String itemId,
        LocalDate serviceDate,
        Instant checkedInAt) {}
