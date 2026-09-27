package com.civicflow.queue.dto.response;

import com.civicflow.queue.entity.QueueTicketEntity;
import java.time.Instant;

public record QueueTicketResponse(
        String id,
        String appointmentId,
        String ticketNo,
        String status,
        Instant checkedInAt,
        String calledWindowId,
        String workSessionId,
        int callCount,
        int version) {
    public static QueueTicketResponse from(QueueTicketEntity ticket) {
        return new QueueTicketResponse(
                ticket.getId().toString(),
                ticket.getAppointmentId().toString(),
                ticket.getTicketNo(),
                ticket.getStatus().name(),
                ticket.getCheckedInAt(),
                ticket.getCalledWindowId() == null ? null : ticket.getCalledWindowId().toString(),
                ticket.getWorkSessionId() == null ? null : ticket.getWorkSessionId().toString(),
                ticket.getCallCount(),
                ticket.getVersion());
    }
}
