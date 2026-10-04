package com.civicflow.queue.event;

import com.civicflow.queue.client.AppointmentCheckInClient;
import com.civicflow.queue.service.impl.QueueTicketCreationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class CheckInClaimedListener {
    private final ObjectMapper json;
    private final QueueTicketCreationService creation;
    private final AppointmentCheckInClient appointments;

    public CheckInClaimedListener(
            ObjectMapper json,
            QueueTicketCreationService creation,
            AppointmentCheckInClient appointments) {
        this.json = json;
        this.creation = creation;
        this.appointments = appointments;
    }

    @RabbitListener(queues = QueueCheckInRabbitConfig.QUEUE)
    public void onMessage(byte[] payload) throws Exception {
        CheckInClaimedEvent event = json.readValue(payload, CheckInClaimedEvent.class);
        if (!"appointment.check-in.claimed".equals(event.eventType())
                || event.eventVersion() != 1
                || event.eventId() == null
                || event.occurredAt() == null
                || event.traceId() == null
                || !"civicflow-appointment".equals(event.producer())
                || event.appointmentId() == null
                || !event.appointmentId().equals(event.idempotencyKey())
                || event.claimId() == null) {
            throw new IllegalArgumentException("Invalid check-in claim event");
        }
        var ticket =
                creation.create(
                        new AppointmentCheckInClient.ClaimResponse(
                                event.claimId(),
                                event.appointmentId(),
                                event.userId(),
                                event.outletId(),
                                event.itemId(),
                                event.serviceDate(),
                                event.checkedInAt(),
                                "CHECKED_IN"));
        appointments.link(
                ticket.getAppointmentId(),
                new AppointmentCheckInClient.LinkRequest(
                        event.claimId(), ticket.getId(), UUID.randomUUID().toString()));
    }
}
