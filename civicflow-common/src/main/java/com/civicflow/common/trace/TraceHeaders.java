package com.civicflow.common.trace;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class TraceHeaders {
    private TraceHeaders() {}

    public static String traceparent(String correlationId) {
        UUID traceId;
        try {
            traceId = UUID.fromString(correlationId);
        } catch (IllegalArgumentException exception) {
            traceId = UUID.nameUUIDFromBytes(correlationId.getBytes(StandardCharsets.UTF_8));
        }
        String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return "00-" + traceId.toString().replace("-", "") + "-" + spanId + "-01";
    }
}
