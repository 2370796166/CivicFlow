package com.civicflow.resource.error;

import com.civicflow.common.api.GlobalErrorCode;

public enum ResourceErrorCode implements GlobalErrorCode {
    VERSION_CONFLICT(
            "RESOURCE_409_VERSION_CONFLICT", "Resource version conflicts with current state"),
    CODE_EXISTS("RESOURCE_409_CODE_EXISTS", "Resource code already exists"),
    SLOT_OVERLAP("RESOURCE_409_SLOT_OVERLAP", "Resource slot overlaps an existing slot"),
    SLOT_STATE_CONFLICT(
            "RESOURCE_409_SLOT_STATE_CONFLICT",
            "Resource slot state does not allow this operation"),
    QUOTA_BELOW_CONSUMED(
            "RESOURCE_409_QUOTA_BELOW_CONSUMED", "Total quota cannot be below consumed quota"),
    CONSUMPTION_UNKNOWN(
            "RESOURCE_409_CONSUMPTION_UNKNOWN",
            "Consumed quota must be synchronized before decreasing released quota"),
    IN_USE("RESOURCE_409_IN_USE", "Resource is in use");

    private final String code;
    private final String message;

    ResourceErrorCode(String code, String message) {
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
