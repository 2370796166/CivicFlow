package com.civicflow.resource.error;

import com.civicflow.common.api.GlobalErrorCode;

public enum ResourceSecurityErrorCode implements GlobalErrorCode {
    UNAUTHORIZED("AUTH_401_UNAUTHORIZED", "Authentication required or token invalid"),
    FORBIDDEN("AUTH_403_FORBIDDEN", "Insufficient permission");

    private final String code;
    private final String message;

    ResourceSecurityErrorCode(String code, String message) {
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
