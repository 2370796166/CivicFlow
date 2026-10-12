package com.civicflow.notification.dto;

import com.civicflow.notification.entity.NotificationRecordEntity;
import java.time.Instant;

public record NotificationView(
        String id,
        String eventId,
        String templateCode,
        int templateVersion,
        String title,
        String body,
        String status,
        Instant createdAt,
        Instant sentAt,
        Instant readAt) {
    public static NotificationView from(NotificationRecordEntity entity) {
        return new NotificationView(
                entity.getId().toString(),
                entity.getEventId(),
                entity.getTemplateCode(),
                entity.getTemplateVersion(),
                entity.getTitle(),
                entity.getBody(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getSentAt(),
                entity.getReadAt());
    }
}
