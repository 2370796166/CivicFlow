package com.civicflow.appointment.dto.response;

import java.time.Instant;
import java.time.LocalDate;

public record CheckInClaimResponse(
        String claimId,
        String appointmentId,
        String userId,
        String outletId,
        String itemId,
        LocalDate serviceDate,
        Instant checkedInAt,
        String status) {}
