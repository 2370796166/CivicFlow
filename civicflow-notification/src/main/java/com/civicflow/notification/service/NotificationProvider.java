package com.civicflow.notification.service;

import com.civicflow.notification.entity.NotificationRecordEntity;

/** Implementations must use record.id as the external provider idempotency key. */
public interface NotificationProvider {
    DeliveryChannel channel();

    void send(NotificationRecordEntity record);
}
