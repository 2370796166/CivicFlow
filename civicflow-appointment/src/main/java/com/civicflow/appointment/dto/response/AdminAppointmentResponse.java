package com.civicflow.appointment.dto.response;

import java.time.Instant;

public record AdminAppointmentResponse(
        String userId,
        String queueTicketId,
        AppointmentResponse appointment,
        Instant confirmedAt,
        Instant cancelledAt,
        Instant expiredAt,
        Instant checkedInAt,
        Instant servingAt,
        Instant completedAt,
        Instant noShowAt) {}
