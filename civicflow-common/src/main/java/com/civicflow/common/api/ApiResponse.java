package com.civicflow.common.api;

import java.util.Objects;

/** Uniform response envelope. HTTP status still carries protocol semantics. */
public record ApiResponse<T>(String code, String message, T data, String requestId) {

    public ApiResponse {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
    }

    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>(
                CommonErrorCode.SUCCESS.code(), CommonErrorCode.SUCCESS.message(), data, requestId);
    }

    public static <T> ApiResponse<T> failure(GlobalErrorCode errorCode, T data, String requestId) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        return new ApiResponse<>(errorCode.code(), errorCode.message(), data, requestId);
    }
}
