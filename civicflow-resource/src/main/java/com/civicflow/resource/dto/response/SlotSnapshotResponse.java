package com.civicflow.resource.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record SlotSnapshotResponse(
        String slotId,
        String outletId,
        String itemId,
        LocalDate serviceDate,
        LocalTime startTime,
        LocalTime endTime,
        int totalQuota,
        Instant releaseAt,
        Instant closeAt,
        String status,
        long configVersion) {}
