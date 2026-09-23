package com.civicflow.appointment.service;

import com.civicflow.appointment.dto.response.ReservationCreateResponse;

public interface ReservationWorkflowService {
    CreateOutcome create(long userId, long slotId, String idempotencyKey, String traceId);

    void recover(long requestId);

    record CreateOutcome(ReservationCreateResponse response, boolean dependencyUnavailable) {}
}
