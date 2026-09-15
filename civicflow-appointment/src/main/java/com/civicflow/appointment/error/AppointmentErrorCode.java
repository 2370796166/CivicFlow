package com.civicflow.appointment.error;

import com.civicflow.common.api.GlobalErrorCode;

public enum AppointmentErrorCode implements GlobalErrorCode {
    DUP_ACTIVE("APPT_409_DUP_ACTIVE", "An active appointment already exists"),
    STOCK_EMPTY("APPT_409_STOCK_EMPTY", "No stock remains for the selected slot"),
    STATE_CONFLICT("APPT_409_STATE_CONFLICT", "Appointment state conflicts with this operation"),
    CONFIRM_TIMEOUT("APPT_409_CONFIRM_TIMEOUT", "Appointment confirmation deadline has passed"),
    RESERVATION_GONE("APPT_410_RESERVATION_GONE", "Reservation has expired or been compensated"),
    SLOT_NOT_OPEN("APPT_422_SLOT_NOT_OPEN", "Resource slot is not open"),
    QR_EXPIRED("SECURITY_410_QR_EXPIRED", "Check-in token has expired or is no longer current");

    private final String code;
    private final String message;

    AppointmentErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
