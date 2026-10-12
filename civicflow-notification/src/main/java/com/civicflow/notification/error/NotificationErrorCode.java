package com.civicflow.notification.error;

import com.civicflow.common.api.GlobalErrorCode;

public enum NotificationErrorCode implements GlobalErrorCode {
    UNAUTHORIZED("NOTIFICATION_401_UNAUTHORIZED", "Unauthorized"),
    FORBIDDEN("NOTIFICATION_403_FORBIDDEN", "Forbidden");

    private final String code;
    private final String message;

    NotificationErrorCode(String code, String message) {
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
