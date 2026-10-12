package com.civicflow.common.api;

import java.time.Instant;

/** Public audit projection. Raw snapshots, credential material and free text are excluded. */
public record OperationLogResponse(
        String id,
        String source,
        String resourceType,
        String resourceId,
        String actorType,
        String actorId,
        String action,
        String outcome,
        String fromStatus,
        String toStatus,
        String requestId,
        Instant occurredAt) {
    public static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
