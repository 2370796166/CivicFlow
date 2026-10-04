package com.civicflow.appointment.error;

import com.civicflow.common.api.GlobalErrorCode;

public enum AppointmentSecurityErrorCode implements GlobalErrorCode {
    UNAUTHORIZED("AUTH_401_UNAUTHORIZED", "Authentication is required"),
    FORBIDDEN("AUTH_403_FORBIDDEN", "Access is forbidden");

    private final String code;
    private final String message;

    AppointmentSecurityErrorCode(String code, String message) {
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
