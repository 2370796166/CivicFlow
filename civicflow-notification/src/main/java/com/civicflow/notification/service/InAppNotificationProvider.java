package com.civicflow.notification.service;

import com.civicflow.notification.entity.NotificationRecordEntity;
import org.springframework.stereotype.Component;

@Component
public class InAppNotificationProvider implements NotificationProvider {
    @Override
    public DeliveryChannel channel() {
        return DeliveryChannel.IN_APP;
    }

    @Override
    public void send(NotificationRecordEntity record) {
        // The transactionally persisted record is the in-app message.
    }
}
