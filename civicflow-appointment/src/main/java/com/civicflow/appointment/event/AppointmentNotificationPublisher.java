package com.civicflow.appointment.event;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.civicflow.appointment.entity.AppointmentOrderEntity;
import com.civicflow.appointment.entity.OutboxEventEntity;
import com.civicflow.appointment.enums.AppointmentStatus;
import com.civicflow.appointment.enums.OutboxStatus;
import com.civicflow.appointment.mapper.AppointmentOrderMapper;
import com.civicflow.appointment.mapper.OutboxEventMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "civicflow.appointment.notification-publisher-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AppointmentNotificationPublisher {
    private final OutboxEventMapper outbox;
    private final AppointmentOrderMapper orders;
    private final RabbitTemplate rabbit;

    public AppointmentNotificationPublisher(
            OutboxEventMapper outbox, AppointmentOrderMapper orders, RabbitTemplate rabbit) {
        this.outbox = outbox;
        this.orders = orders;
        this.rabbit = rabbit;
    }

    @Scheduled(fixedDelayString = "${civicflow.appointment.notification-publish-delay-ms:1000}")
    public void publish() {
        for (OutboxEventEntity candidate : outbox.selectDueNotification(50)) {
            if (outbox.claim(candidate.getId()) != 1) continue;
            OutboxEventEntity entry = outbox.selectById(candidate.getId());
            if ("appointment.notification.reminder".equals(entry.getEventType())) {
                AppointmentOrderEntity order =
                        orders.selectById(Long.parseLong(entry.getAggregateId()));
                if (order == null || order.getStatus() != AppointmentStatus.PENDING_CONFIRM) {
                    update(entry, OutboxStatus.PUBLISHED, null);
                    continue;
                }
            }
            try {
                MessageProperties properties = new MessageProperties();
                properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                Message message =
                        new Message(
                                entry.getPayloadJson().getBytes(StandardCharsets.UTF_8),
                                properties);
                CorrelationData correlation = new CorrelationData(entry.getEventId());
                rabbit.send(
                        AppointmentMessaging.APPOINTMENT_EXCHANGE,
                        entry.getRoutingKey(),
                        message,
                        correlation);
                var confirm = correlation.getFuture().get(3, TimeUnit.SECONDS);
                if (confirm.isAck() && correlation.getReturned() == null) {
                    update(entry, OutboxStatus.PUBLISHED, null);
                } else {
                    update(entry, OutboxStatus.FAILED, "PUBLISH_REJECTED");
                }
            } catch (Exception exception) {
                update(entry, OutboxStatus.FAILED, "PUBLISH_UNKNOWN");
            }
        }
    }

    private void update(OutboxEventEntity entry, OutboxStatus status, String code) {
        OutboxEventEntity change = new OutboxEventEntity();
        change.setStatus(status);
        change.setLastErrorCode(code);
        change.setPublishedAt(status == OutboxStatus.PUBLISHED ? Instant.now() : null);
        change.setNextAttemptAt(Instant.now().plusSeconds(30));
        outbox.update(
                change,
                Wrappers.<OutboxEventEntity>lambdaUpdate()
                        .eq(OutboxEventEntity::getId, entry.getId())
                        .eq(OutboxEventEntity::getStatus, OutboxStatus.PUBLISHING));
    }
}
