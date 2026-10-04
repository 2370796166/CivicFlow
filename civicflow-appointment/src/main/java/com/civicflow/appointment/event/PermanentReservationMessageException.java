package com.civicflow.appointment.event;

public class PermanentReservationMessageException extends RuntimeException {
    public PermanentReservationMessageException(String message) {
        super(message);
    }

    public PermanentReservationMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
