package com.civicflow.queue.dto.response;

import com.civicflow.queue.entity.WindowWorkSessionEntity;
import java.time.Instant;

public record WorkSessionResponse(
        String id,
        String outletId,
        String windowId,
        String status,
        int version,
        Instant startedAt,
        Instant endedAt) {
    public static WorkSessionResponse from(WindowWorkSessionEntity session) {
        return new WorkSessionResponse(
                session.getId().toString(),
                session.getOutletId().toString(),
                session.getWindowId().toString(),
                session.getStatus().name(),
                session.getVersion(),
                session.getStartedAt(),
                session.getEndedAt());
    }
}
