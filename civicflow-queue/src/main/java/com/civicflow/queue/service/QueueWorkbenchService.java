package com.civicflow.queue.service;

import com.civicflow.queue.dto.response.CurrentWorkSessionResponse;
import com.civicflow.queue.dto.response.QueueProgressResponse;
import com.civicflow.queue.dto.response.QueueTicketResponse;
import com.civicflow.queue.dto.response.WorkSessionResponse;
import java.time.LocalDate;
import java.util.List;

public interface QueueWorkbenchService {
    WorkSessionResponse start(long staff, long window, String key, String requestId);

    WorkSessionResponse end(long staff, long session, int version, String key, String requestId);

    CurrentWorkSessionResponse currentSession(long staff, long window);

    QueueTicketResponse callNext(
            long staff, long session, List<Long> itemIds, String key, String requestId);

    QueueTicketResponse change(
            long staff,
            long session,
            long ticket,
            int version,
            String action,
            String result,
            String key,
            String requestId);

    List<QueueProgressResponse> own(long user, Long appointment);

    QueueProgressResponse ownTicket(long user, long ticket);

    Object overview(long outlet, LocalDate date);
}
