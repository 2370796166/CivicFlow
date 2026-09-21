package com.civicflow.appointment.support;

import java.util.Arrays;

public enum CompensationScriptResultCode {
    RELEASED(0),
    ALREADY_RELEASED(1),
    NEED_RECONCILE(20),
    INVARIANT_BROKEN(21),
    OCCUPANCY_MISMATCH(22),
    RESERVATION_CONFLICT(23);

    private final int value;

    CompensationScriptResultCode(int value) {
        this.value = value;
    }

    public static CompensationScriptResultCode from(int value) {
        return Arrays.stream(values())
                .filter(code -> code.value == value)
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Unknown compensation Lua result code: " + value));
    }
}
