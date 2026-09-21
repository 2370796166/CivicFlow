package com.civicflow.appointment.support;

import java.time.Instant;
import java.time.LocalDate;

public record SlotStockSnapshot(
        long slotId,
        long outletId,
        long itemId,
        LocalDate serviceDate,
        int totalQuota,
        Instant releaseAt,
        Instant closeAt,
        String status,
        long configVersion) {
    public SlotStockSnapshot {
        if (slotId <= 0
                || outletId <= 0
                || itemId <= 0
                || totalQuota < 0
                || configVersion <= 0
                || !closeAt.isAfter(releaseAt)) {
            throw new IllegalArgumentException("Invalid slot stock snapshot");
        }
    }
}
