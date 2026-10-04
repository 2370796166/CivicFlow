package com.civicflow.appointment.event;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.civicflow.appointment.config.AppointmentProperties;
import com.civicflow.appointment.entity.OutboxEventEntity;
import com.civicflow.appointment.enums.OutboxStatus;
import com.civicflow.appointment.mapper.OutboxEventMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CheckInClaimPublisher {
    private final OutboxEventMapper outbox;
    private final RabbitTemplate rabbit;
    private final AppointmentProperties properties;
    private final Clock clock;

    public CheckInClaimPublisher(
            OutboxEventMapper outbox,
            RabbitTemplate rabbit,
            AppointmentProperties properties,
            Clock clock) {
        this.outbox = outbox;
        this.rabbit = rabbit;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${civicflow.appointment.check-in.publish-delay:5s}")
    public void publishDue() {
        if (!properties.getRecovery().isEnabled()) {
            return;
        }
        for (OutboxEventEntity entry :
                outbox.selectDueCheckIn(properties.getRecovery().getBatchSize())) {
            if (outbox.claim(entry.getId()) != 1) {
                continue;
            }
            boolean ack = false;
            try {
                MessageProperties messageProperties = new MessageProperties();
                messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                messageProperties.setMessageId(entry.getEventId());
                CorrelationData correlation = new CorrelationData(entry.getEventId());
                rabbit.send(
                        AppointmentMessaging.APPOINTMENT_EXCHANGE,
                        entry.getRoutingKey(),
                        new Message(
                                entry.getPayloadJson().getBytes(StandardCharsets.UTF_8),
                                messageProperties),
                        correlation);
                var confirm =
                        correlation
                                .getFuture()
                                .get(
                                        properties
                                                .getReservation()
                                                .getPublishConfirmTimeout()
                                                .toMillis(),
                                        TimeUnit.MILLISECONDS);
                ack = confirm.isAck() && correlation.getReturned() == null;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (Exception exception) {
                // The same eventId is retried after the lease; no event content is logged.
            }
            OutboxEventEntity update = new OutboxEventEntity();
            update.setStatus(ack ? OutboxStatus.PUBLISHED : OutboxStatus.FAILED);
            update.setPublishedAt(ack ? clock.instant() : null);
            update.setNextAttemptAt(clock.instant().plus(properties.getRecovery().getRetryDelay()));
            update.setLastErrorCode(ack ? null : "PUBLISH_UNKNOWN");
            outbox.update(
                    update,
                    Wrappers.<OutboxEventEntity>lambdaUpdate()
                            .eq(OutboxEventEntity::getId, entry.getId())
                            .eq(OutboxEventEntity::getStatus, OutboxStatus.PUBLISHING));
        }
    }
}
