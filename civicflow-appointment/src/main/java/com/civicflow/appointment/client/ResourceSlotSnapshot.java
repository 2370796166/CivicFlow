package com.civicflow.appointment.client;

import com.civicflow.appointment.support.SlotStockSnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record ResourceSlotSnapshot(
        String slotId,
        String outletId,
        String itemId,
        String outletName,
        String itemName,
        LocalDate serviceDate,
        LocalTime startTime,
        LocalTime endTime,
        int totalQuota,
        Instant releaseAt,
        Instant checkInStart,
        Instant checkInEnd,
        Instant closeAt,
        String status,
        long configVersion) {
    public SlotStockSnapshot toStockSnapshot() {
        return new SlotStockSnapshot(
                parse(slotId),
                parse(outletId),
                parse(itemId),
                serviceDate,
                totalQuota,
                releaseAt,
                closeAt,
                status,
                configVersion);
    }

    private static long parse(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Resource returned an invalid identifier", exception);
        }
    }
}
