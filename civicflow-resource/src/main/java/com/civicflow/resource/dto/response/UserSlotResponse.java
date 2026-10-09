package com.civicflow.resource.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** Public booking configuration; inventory is decided by the reservation service. */
public record UserSlotResponse(
        String id,
        String outletId,
        String itemId,
        LocalDate serviceDate,
        LocalTime startTime,
        LocalTime endTime,
        int totalQuota,
        Instant releaseAt,
        Instant closeAt,
        String bookingStatus) {}
