package com.civicflow.appointment.dto.response;

import java.time.Instant;

/** An audit projection intentionally excludes internal detail JSON and QR credentials. */
public record AppointmentLogResponse(
        String id,
        String appointmentId,
        String reservationId,
        String actorType,
        String actorId,
        String operation,
        String fromStatus,
        String toStatus,
        String requestId,
        Instant occurredAt) {}
