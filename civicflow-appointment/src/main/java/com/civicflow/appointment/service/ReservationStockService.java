package com.civicflow.appointment.service;

import com.civicflow.appointment.support.SlotStockSnapshot;

public interface ReservationStockService {
    ReservationResult reserve(SlotStockSnapshot snapshot, long userId, String reservationId);

    CompensationResult compensate(
            SlotStockSnapshot snapshot, long userId, String reservationId, String reason);

    record ReservationResult(
            String reservationId, boolean idempotent, long remaining, long configVersion) {}

    record CompensationResult(boolean idempotent, long remaining, long configVersion) {}
}
