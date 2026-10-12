package com.civicflow.notification.service;

import com.civicflow.common.api.PageResponse;
import com.civicflow.notification.dto.NotificationFailureView;
import com.civicflow.notification.dto.NotificationView;
import com.civicflow.notification.event.NotificationEvent;

public interface NotificationService {
    void consume(NotificationEvent event, String eventJson);

    PageResponse<NotificationView> own(long userId, int page, int size);

    PageResponse<NotificationFailureView> failures(int page, int size);

    void read(long userId, long recordId);

    boolean preference(long userId);

    void preference(long userId, boolean enabled);

    void replay(long adminId, long recordId, String reason);
}
