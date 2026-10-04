package com.civicflow.resource.dto.response;

import com.civicflow.resource.enums.SlotStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record SlotResponse(
        String id,
        String outletId,
        String itemId,
        LocalDate serviceDate,
        LocalTime startTime,
        LocalTime endTime,
        int totalQuota,
        Instant releaseAt,
        Instant checkInStart,
        Instant checkInEnd,
        SlotStatus status,
        long configVersion,
        Integer consumedHint,
        int version) {}
