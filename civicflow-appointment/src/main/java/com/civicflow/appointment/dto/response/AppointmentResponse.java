package com.civicflow.appointment.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record AppointmentResponse(
        String appointmentId,
        String reservationId,
        String slotId,
        String outletId,
        String itemId,
        String outletName,
        String itemName,
        LocalDate serviceDate,
        LocalTime slotStartTime,
        LocalTime slotEndTime,
        String status,
        Instant confirmDeadline,
        int version,
        Instant createdAt,
        Instant updatedAt) {}
