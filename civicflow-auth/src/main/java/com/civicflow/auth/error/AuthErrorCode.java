package com.civicflow.auth.error;

import com.civicflow.common.api.GlobalErrorCode;

public enum AuthErrorCode implements GlobalErrorCode {
    UNAUTHORIZED("AUTH_401_UNAUTHORIZED", "Authentication required or token is invalid"),
    REFRESH_REUSED("AUTH_401_REFRESH_REUSED", "Refresh token reuse detected"),
    FORBIDDEN("AUTH_403_FORBIDDEN", "Access denied");

    private final String code;
    private final String message;

    AuthErrorCode(String code, String message) {
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
