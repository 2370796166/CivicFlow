package com.civicflow.appointment.support;

public enum ReservationStateScriptResultCode {
    UPDATED(0),
    IDEMPOTENT_OK(1),
    NEED_RECONCILE(20),
    RESERVATION_CONFLICT(23);

    private final long code;

    ReservationStateScriptResultCode(long code) {
        this.code = code;
    }

    public static ReservationStateScriptResultCode from(long code) {
        for (ReservationStateScriptResultCode value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown reservation state Lua result: " + code);
    }
}
