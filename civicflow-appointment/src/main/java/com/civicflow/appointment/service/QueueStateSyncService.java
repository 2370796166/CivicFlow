package com.civicflow.appointment.service;

public interface QueueStateSyncService {
    void apply(
            long appointmentId, long ticketId, String queueStatus, String syncId, String requestId);
}
