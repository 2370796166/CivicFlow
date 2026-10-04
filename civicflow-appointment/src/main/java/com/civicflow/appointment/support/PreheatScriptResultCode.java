package com.civicflow.appointment.support;

import java.util.Arrays;

public enum PreheatScriptResultCode {
    APPLIED(0),
    IDEMPOTENT(1),
    STALE(2),
    REQUIRES_ADJUST(3),
    VERSION_GAP(4),
    UNSAFE_MISSING(5),
    SNAPSHOT_CONFLICT(6),
    QUOTA_BELOW_CONSUMED(7);

    private final int value;

    PreheatScriptResultCode(int value) {
        this.value = value;
    }

    public static PreheatScriptResultCode from(int value) {
        return Arrays.stream(values())
                .filter(code -> code.value == value)
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Unknown preheat Lua result code: " + value));
    }
}
