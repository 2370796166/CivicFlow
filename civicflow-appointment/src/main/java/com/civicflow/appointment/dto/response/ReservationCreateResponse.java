package com.civicflow.appointment.dto.response;

public record ReservationCreateResponse(
        String reservationId, String status, int pollAfterMs, String failureCode) {}
