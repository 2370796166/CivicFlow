package com.civicflow.appointment.event;

import com.civicflow.appointment.entity.AppointmentOrderEntity;
import com.civicflow.appointment.enums.AppointmentStatus;
import com.civicflow.appointment.mapper.AppointmentOrderMapper;
import com.civicflow.appointment.service.AppointmentStateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.time.Clock;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ConfirmTimeoutListener {
    private final ObjectMapper json;
    private final AppointmentOrderMapper orders;
    private final AppointmentStateService states;
    private final ConfirmTimeoutPublisher publisher;
    private final Clock clock;

    public ConfirmTimeoutListener(
            ObjectMapper json,
            AppointmentOrderMapper orders,
            AppointmentStateService states,
            ConfirmTimeoutPublisher publisher,
            Clock clock) {
        this.json = json;
        this.orders = orders;
        this.states = states;
        this.publisher = publisher;
        this.clock = clock;
    }

    @RabbitListener(
            queues = AppointmentMessaging.TIMEOUT_CHECK_QUEUE,
            containerFactory = "reservationListenerContainerFactory",
            ackMode = "MANUAL")
    public void consume(Message message, Channel channel) throws IOException {
        ConfirmTimeoutEvent event;
        try {
            event = json.readValue(message.getBody(), ConfirmTimeoutEvent.class);
        } catch (Exception exception) {
            throw new PermanentReservationMessageException("Malformed timeout event", exception);
        }
        if (event.schemaVersion() != 1
                || event.eventVersion() != 1
                || !AppointmentMessaging.TIMEOUT_EVENT_TYPE.equals(event.eventType())
                || !"civicflow-appointment".equals(event.producer())
                || event.eventId() == null
                || event.traceId() == null
                || event.payload() == null
                || event.payload().reservationId() == null
                || event.payload().appointmentId() == null
                || event.payload().confirmDeadline() == null) {
            throw new PermanentReservationMessageException("Unsupported timeout event");
        }
        if (!("timeout:" + event.payload().reservationId()).equals(event.idempotencyKey())) {
            throw new PermanentReservationMessageException("Timeout idempotency key mismatch");
        }
        long id;
        try {
            id = Long.parseLong(event.payload().appointmentId());
        } catch (NumberFormatException exception) {
            throw new PermanentReservationMessageException("Invalid appointment id", exception);
        }
        AppointmentOrderEntity order = orders.selectById(id);
        if (order == null
                || !order.getReservationId().equals(event.payload().reservationId())
                || !order.getConfirmDeadline().equals(event.payload().confirmDeadline())) {
            throw new PermanentReservationMessageException(
                    "Timeout event does not match appointment");
        }
        if (order.getStatus() == AppointmentStatus.PENDING_CONFIRM
                && clock.instant().isBefore(order.getConfirmDeadline())) {
            publisher.reschedule(event);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            return;
        }
        states.expire(id, event.traceId());
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }
}
