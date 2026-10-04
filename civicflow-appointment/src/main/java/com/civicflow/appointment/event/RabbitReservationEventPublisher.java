package com.civicflow.appointment.event;

import com.civicflow.appointment.config.AppointmentProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitReservationEventPublisher implements ReservationEventPublisher {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(RabbitReservationEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final AppointmentProperties properties;

    public RabbitReservationEventPublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            AppointmentProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public PublishResult publish(ReservationRequestedEvent event) {
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(event);
        } catch (JsonProcessingException exception) {
            return new PublishResult(Outcome.FAILED, 0, "PUBLISH_SERIALIZATION_FAILED");
        }
        boolean unknownSeen = false;
        String lastError = "PUBLISH_FAILED";
        int maximum = properties.getReservation().getPublishMaxAttempts();
        for (int attempt = 1; attempt <= maximum; attempt++) {
            CorrelationData correlation = new CorrelationData(event.eventId() + ":" + attempt);
            try {
                Message message = message(event, body);
                rabbitTemplate.send(
                        AppointmentMessaging.APPOINTMENT_EXCHANGE,
                        AppointmentMessaging.RESERVATION_REQUESTED_ROUTING_KEY,
                        message,
                        correlation);
                CorrelationData.Confirm confirm =
                        correlation
                                .getFuture()
                                .get(
                                        properties
                                                .getReservation()
                                                .getPublishConfirmTimeout()
                                                .toMillis(),
                                        TimeUnit.MILLISECONDS);
                if (confirm.isAck() && correlation.getReturned() == null) {
                    return new PublishResult(Outcome.ACKNOWLEDGED, attempt, null);
                }
                lastError = correlation.getReturned() != null ? "PUBLISH_RETURNED" : "PUBLISH_NACK";
            } catch (TimeoutException exception) {
                unknownSeen = true;
                lastError = "PUBLISH_CONFIRM_TIMEOUT";
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return new PublishResult(Outcome.UNKNOWN, attempt, "PUBLISH_INTERRUPTED");
            } catch (AmqpException | java.util.concurrent.ExecutionException exception) {
                unknownSeen = true;
                lastError = "PUBLISH_TRANSPORT_UNKNOWN";
                LOGGER.warn(
                        "Reservation publish attempt failed eventId={} attempt={} type={}",
                        event.eventId(),
                        attempt,
                        exception.getClass().getSimpleName());
            }
        }
        return new PublishResult(
                unknownSeen ? Outcome.UNKNOWN : Outcome.FAILED, maximum, lastError);
    }

    private Message message(ReservationRequestedEvent event, byte[] body) {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        messageProperties.setContentEncoding("UTF-8");
        messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        messageProperties.setMessageId(event.eventId());
        messageProperties.setCorrelationId(event.correlationId());
        messageProperties.setHeader("schemaVersion", event.schemaVersion());
        messageProperties.setHeader("eventType", event.eventType());
        messageProperties.setHeader("eventVersion", event.eventVersion());
        messageProperties.setHeader("traceId", event.traceId());
        return new Message(body, messageProperties);
    }
}
