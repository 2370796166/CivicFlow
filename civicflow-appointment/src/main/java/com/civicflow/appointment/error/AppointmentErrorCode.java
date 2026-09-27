package com.civicflow.appointment.error;

import com.civicflow.common.api.GlobalErrorCode;

public enum AppointmentErrorCode implements GlobalErrorCode {
    DUP_ACTIVE("APPT_409_DUP_ACTIVE", "An active appointment already exists"),
    STOCK_EMPTY("APPT_409_STOCK_EMPTY", "No stock remains for the selected slot"),
    STATE_CONFLICT("APPT_409_STATE_CONFLICT", "Appointment state conflicts with this operation"),
    CONFIRM_TIMEOUT("APPT_409_CONFIRM_TIMEOUT", "Appointment confirmation deadline has passed"),
    STOCK_REBUILD_UNSAFE(
            "APPT_409_STOCK_REBUILD_UNSAFE",
            "Released stock cannot be rebuilt without complete consumption evidence"),
    SLOT_VERSION_GAP(
            "APPT_409_SLOT_VERSION_GAP", "Slot stock configuration version is not continuous"),
    STOCK_INVARIANT_BROKEN("APPT_409_STOCK_INVARIANT_BROKEN", "Slot stock requires reconciliation"),
    RESERVATION_GONE("APPT_410_RESERVATION_GONE", "Reservation has expired or been compensated"),
    SLOT_NOT_OPEN("APPT_422_SLOT_NOT_OPEN", "Resource slot is not open"),
    PUBLISH_FAILED(
            "APPT_503_PUBLISH_FAILED", "Reservation publication failed and stock was released"),
    PUBLISH_UNKNOWN("APPT_503_PUBLISH_UNKNOWN", "Reservation publication result is not yet known"),
    COMPENSATION_PENDING("APPT_503_COMPENSATION_PENDING", "Stock compensation is pending retry"),
    MESSAGE_CONFLICT(
            "APPT_409_MESSAGE_CONFLICT", "Message idempotency key is bound to another payload"),
    QR_EXPIRED("SECURITY_410_QR_EXPIRED", "Check-in token has expired or is no longer current"),
    CHECKIN_WINDOW("QUEUE_422_CHECKIN_WINDOW", "Check-in window, date, or outlet does not match");

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
