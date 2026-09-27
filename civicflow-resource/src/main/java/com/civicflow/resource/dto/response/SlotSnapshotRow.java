package com.civicflow.resource.dto.response;

import com.civicflow.resource.enums.SlotStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record SlotSnapshotRow(
        Long slotId,
        Long outletId,
        Long itemId,
        String outletName,
        String itemName,
        LocalDate serviceDate,
        LocalTime startTime,
        LocalTime endTime,
        int totalQuota,
        Instant releaseAt,
        Instant checkInStart,
        Instant checkInEnd,
        SlotStatus status,
        long configVersion) {}
