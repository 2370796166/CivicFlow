package com.civicflow.gateway.error;

import com.civicflow.common.api.GlobalErrorCode;

public enum GatewayErrorCode implements GlobalErrorCode {
    BAD_REQUEST("COMMON_400_VALIDATION", "Invalid request"),
    UNAUTHORIZED("AUTH_401_UNAUTHORIZED", "Authentication required or token is invalid"),
    FORBIDDEN("AUTH_403_FORBIDDEN", "Access denied"),
    RATE_LIMITED("GATEWAY_429_RATE_LIMITED", "Too many requests"),
    DEPENDENCY_UNAVAILABLE("COMMON_503_DEPENDENCY_UNAVAILABLE", "Dependency unavailable"),
    INTERNAL_ERROR("COMMON_500_INTERNAL_ERROR", "Internal server error");

    private final String code;
    private final String message;

    GatewayErrorCode(String code, String message) {
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
