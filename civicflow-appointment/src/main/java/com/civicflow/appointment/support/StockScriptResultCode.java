package com.civicflow.appointment.support;

import java.util.Arrays;

public enum StockScriptResultCode {
    OK(0),
    IDEMPOTENT_OK(1),
    SLOT_NOT_FOUND(10),
    NOT_RELEASED(11),
    SLOT_NOT_OPEN(12),
    SLOT_CLOSED(13),
    CONFIG_VERSION_MISMATCH(14),
    DUP_ACTIVE(15),
    OUT_OF_STOCK(16),
    RESERVATION_CONFLICT(17),
    ACTIVE_WRITE_FAILED(18);

    private final int value;

    StockScriptResultCode(int value) {
        this.value = value;
    }

    public static StockScriptResultCode from(int value) {
        return Arrays.stream(values())
                .filter(code -> code.value == value)
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Unknown reserve Lua result code: " + value));
    }
}
