package com.civicflow.queue.error;

import com.civicflow.common.api.GlobalErrorCode;

public enum QueueErrorCode implements GlobalErrorCode {
    SESSION_ACTIVE("QUEUE_409_SESSION_ACTIVE", "Window already has an active session"),
    STATE_CONFLICT("QUEUE_409_STATE_CONFLICT", "Queue state or window ownership conflicts"),
    CHECKIN_WINDOW("QUEUE_422_CHECKIN_WINDOW", "Check-in window, date, or outlet does not match"),
    CHECKIN_PENDING("QUEUE_503_CHECKIN_PENDING", "Check-in is pending recovery"),
    QR_EXPIRED("SECURITY_410_QR_EXPIRED", "Check-in token has expired or is no longer current");

    private final String code;
    private final String message;

    QueueErrorCode(String code, String message) {
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
