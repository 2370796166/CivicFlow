package com.civicflow.appointment.support;

import java.util.List;

public record LuaResult(int code, long remaining, long configVersion) {
    public static LuaResult from(List<?> raw) {
        if (raw == null || raw.size() != 3) {
            throw new IllegalStateException("Lua result must contain exactly three numeric values");
        }
        return new LuaResult(
                number(raw.get(0)).intValue(),
                number(raw.get(1)).longValue(),
                number(raw.get(2)).longValue());
    }

    private static Number number(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("Lua result value is not numeric");
        }
        return number;
    }
}
