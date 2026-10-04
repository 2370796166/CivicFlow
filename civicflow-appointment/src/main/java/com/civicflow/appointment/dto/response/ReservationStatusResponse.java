package com.civicflow.appointment.dto.response;

public record ReservationStatusResponse(
        String reservationId, String status, AppointmentResponse appointment, String failureCode) {}
