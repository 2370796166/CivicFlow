package com.civicflow.queue.service;

import com.civicflow.queue.dto.response.QueueTicketResponse;

public interface CheckInService {
    QueueTicketResponse selfCheckIn(long userId, long outletId, String token);

    QueueTicketResponse staffCheckIn(long staffUserId, long outletId, String token);
}
