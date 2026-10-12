package com.civicflow.notification.dto;

import com.civicflow.notification.entity.NotificationRecordEntity;
import java.time.Instant;

public record NotificationFailureView(
        String id,
        String eventId,
        String channel,
        String userId,
        String templateCode,
        String status,
        int attempts,
        String lastErrorCode,
        Instant createdAt) {
    public static NotificationFailureView from(NotificationRecordEntity record) {
        return new NotificationFailureView(
                record.getId().toString(),
                record.getEventId(),
                record.getChannel(),
                record.getUserId().toString(),
                record.getTemplateCode(),
                record.getStatus(),
                record.getAttempts(),
                record.getLastErrorCode(),
                record.getCreatedAt());
    }
}
