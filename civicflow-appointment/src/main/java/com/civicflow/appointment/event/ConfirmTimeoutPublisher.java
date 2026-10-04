package com.civicflow.appointment.event;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.civicflow.appointment.config.AppointmentProperties;
import com.civicflow.appointment.entity.OutboxEventEntity;
import com.civicflow.appointment.enums.OutboxStatus;
import com.civicflow.appointment.mapper.OutboxEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ConfirmTimeoutPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfirmTimeoutPublisher.class);
    private final OutboxEventMapper outbox;
    private final RabbitTemplate rabbit;
    private final ObjectMapper json;
    private final AppointmentProperties properties;
    private final Clock clock;

    public ConfirmTimeoutPublisher(
            OutboxEventMapper outbox,
            RabbitTemplate rabbit,
            ObjectMapper json,
            AppointmentProperties properties,
            Clock clock) {
        this.outbox = outbox;
        this.rabbit = rabbit;
        this.json = json;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${civicflow.appointment.timeout.publish-delay:5s}")
    public void publishDue() {
        if (!properties.getRecovery().isEnabled()) {
            return;
        }
        for (OutboxEventEntity candidate :
                outbox.selectDue(properties.getRecovery().getBatchSize())) {
            if (outbox.claim(candidate.getId()) == 1) {
                publish(candidate);
            }
        }
    }

    public void reschedule(ConfirmTimeoutEvent event) {
        long remaining =
                Duration.between(clock.instant(), event.payload().confirmDeadline()).toMillis();
        if (remaining <= 0) {
            return;
        }
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        props.setMessageId(event.eventId());
        props.setExpiration(Long.toString(Math.max(1, Math.min(300_000, remaining))));
        try {
            CorrelationData correlation = new CorrelationData(event.eventId() + ":early");
            rabbit.send(
                    AppointmentMessaging.TIMEOUT_EXCHANGE,
                    AppointmentMessaging.TIMEOUT_SCHEDULE_ROUTING_KEY,
                    new Message(json.writeValueAsBytes(event), props),
                    correlation);
            var confirmation =
                    correlation
                            .getFuture()
                            .get(
                                    properties
                                            .getReservation()
                                            .getPublishConfirmTimeout()
                                            .toMillis(),
                                    TimeUnit.MILLISECONDS);
            if (!confirmation.isAck() || correlation.getReturned() != null) {
                throw new IllegalStateException("Early timeout rescheduling was not acknowledged");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Early timeout rescheduling interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Early timeout rescheduling failed", exception);
        }
    }

    private void publish(OutboxEventEntity entry) {
        boolean acknowledged = false;
        String error = "PUBLISH_UNKNOWN";
        try {
            ConfirmTimeoutEvent event =
                    json.readValue(entry.getPayloadJson(), ConfirmTimeoutEvent.class);
            long remaining =
                    Duration.between(clock.instant(), event.payload().confirmDeadline()).toMillis();
            String routing =
                    remaining <= 0
                            ? AppointmentMessaging.TIMEOUT_CHECK_ROUTING_KEY
                            : AppointmentMessaging.TIMEOUT_SCHEDULE_ROUTING_KEY;
            MessageProperties props = new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            props.setMessageId(event.eventId());
            if (remaining > 0) {
                props.setExpiration(Long.toString(Math.max(1, Math.min(300_000, remaining))));
            }
            CorrelationData correlation =
                    new CorrelationData(event.eventId() + ":" + entry.getAttempts());
            rabbit.send(
                    AppointmentMessaging.TIMEOUT_EXCHANGE,
                    routing,
                    new Message(entry.getPayloadJson().getBytes(StandardCharsets.UTF_8), props),
                    correlation);
            var confirmation =
                    correlation
                            .getFuture()
                            .get(
                                    properties
                                            .getReservation()
                                            .getPublishConfirmTimeout()
                                            .toMillis(),
                                    TimeUnit.MILLISECONDS);
            acknowledged = confirmation.isAck() && correlation.getReturned() == null;
            error =
                    correlation.getReturned() != null
                            ? "PUBLISH_RETURNED"
                            : confirmation.isAck() ? null : "PUBLISH_NACK";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            LOGGER.warn(
                    "Timeout publish pending retry eventId={} type={}",
                    entry.getEventId(),
                    exception.getClass().getSimpleName());
        }
        OutboxEventEntity change = new OutboxEventEntity();
        change.setStatus(acknowledged ? OutboxStatus.PUBLISHED : OutboxStatus.FAILED);
        change.setPublishedAt(acknowledged ? clock.instant() : null);
        change.setLastErrorCode(error);
        change.setNextAttemptAt(clock.instant().plus(properties.getRecovery().getRetryDelay()));
        outbox.update(
                change,
                Wrappers.<OutboxEventEntity>lambdaUpdate()
                        .eq(OutboxEventEntity::getId, entry.getId())
                        .eq(OutboxEventEntity::getStatus, OutboxStatus.PUBLISHING));
    }
}
