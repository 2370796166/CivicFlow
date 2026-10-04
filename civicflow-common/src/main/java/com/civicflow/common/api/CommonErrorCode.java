package com.civicflow.common.api;

/** Cross-service errors only. Service-specific errors remain in their owning service. */
public enum CommonErrorCode implements GlobalErrorCode {
    SUCCESS("OK", "success"),
    VALIDATION("COMMON_400_VALIDATION", "Invalid request"),
    IDEMPOTENCY_REQUIRED("COMMON_400_IDEMPOTENCY_REQUIRED", "Idempotency-Key is required"),
    NOT_FOUND("COMMON_404_NOT_FOUND", "Resource not found"),
    IDEMPOTENCY_CONFLICT(
            "COMMON_409_IDEMPOTENCY_CONFLICT", "Idempotency key is bound to another payload"),
    DEPENDENCY_UNAVAILABLE("COMMON_503_DEPENDENCY_UNAVAILABLE", "Dependency unavailable"),
    INTERNAL_ERROR("COMMON_500_INTERNAL_ERROR", "Internal server error");

    private final String code;
    private final String message;

    CommonErrorCode(String code, String message) {
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
