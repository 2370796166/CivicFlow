package com.civicflow.queue.event;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.civicflow.queue.entity.OutboxEventEntity;
import com.civicflow.queue.entity.QueueTicketEntity;
import com.civicflow.queue.enums.OutboxStatus;
import com.civicflow.queue.enums.QueueTicketStatus;
import com.civicflow.queue.mapper.QueueNotificationOutboxMapper;
import com.civicflow.queue.mapper.QueueTicketMapper;
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
        name = "civicflow.queue.notification-publisher-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class QueueNotificationPublisher {
    private final QueueNotificationOutboxMapper outbox;
    private final QueueTicketMapper tickets;
    private final RabbitTemplate rabbit;

    public QueueNotificationPublisher(
            QueueNotificationOutboxMapper outbox,
            QueueTicketMapper tickets,
            RabbitTemplate rabbit) {
        this.outbox = outbox;
        this.tickets = tickets;
        this.rabbit = rabbit;
    }

    @Scheduled(fixedDelayString = "${civicflow.queue.notification-publish-delay-ms:1000}")
    public void publish() {
        for (OutboxEventEntity candidate : outbox.due(50)) {
            if (outbox.claim(candidate.getId()) != 1) continue;
            OutboxEventEntity entry = outbox.selectById(candidate.getId());
            if ("queue.notification.near".equals(entry.getEventType())) {
                QueueTicketEntity ticket =
                        tickets.selectById(Long.parseLong(entry.getAggregateId()));
                if (ticket == null || ticket.getStatus() != QueueTicketStatus.WAITING) {
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
                        QueueNotificationOutbox.EXCHANGE,
                        entry.getRoutingKey(),
                        message,
                        correlation);
                var confirm = correlation.getFuture().get(3, TimeUnit.SECONDS);
                update(
                        entry,
                        confirm.isAck() && correlation.getReturned() == null
                                ? OutboxStatus.PUBLISHED
                                : OutboxStatus.FAILED,
                        confirm.isAck() ? "RETURNED" : "NACK");
            } catch (Exception exception) {
                update(entry, OutboxStatus.FAILED, "PUBLISH_UNKNOWN");
            }
        }
    }

    private void update(OutboxEventEntity entry, OutboxStatus status, String code) {
        OutboxEventEntity change = new OutboxEventEntity();
        change.setStatus(status);
        change.setLastErrorCode(status == OutboxStatus.PUBLISHED ? null : code);
        change.setPublishedAt(status == OutboxStatus.PUBLISHED ? Instant.now() : null);
        change.setNextAttemptAt(Instant.now().plusSeconds(30));
        outbox.update(
                change,
                Wrappers.<OutboxEventEntity>lambdaUpdate()
                        .eq(OutboxEventEntity::getId, entry.getId())
                        .eq(OutboxEventEntity::getStatus, OutboxStatus.PUBLISHING));
    }
}
