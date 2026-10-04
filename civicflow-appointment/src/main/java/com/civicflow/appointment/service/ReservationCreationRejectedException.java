package com.civicflow.appointment.service;

import com.civicflow.appointment.entity.AppointmentReservationRequestEntity;

public class ReservationCreationRejectedException extends RuntimeException {
    private final AppointmentReservationRequestEntity request;
    private final String failureCode;

    public ReservationCreationRejectedException(
            AppointmentReservationRequestEntity request, String failureCode, String message) {
        super(message);
        this.request = request;
        this.failureCode = failureCode;
    }

    public AppointmentReservationRequestEntity request() {
        return request;
    }

    public String failureCode() {
        return failureCode;
    }
}
