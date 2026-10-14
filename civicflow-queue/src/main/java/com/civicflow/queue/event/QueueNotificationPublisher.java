package com.civicflow.queue.event;

import com.civicflow.queue.entity.OutboxEventEntity;
import com.civicflow.queue.entity.QueueTicketEntity;
import com.civicflow.queue.enums.OutboxStatus;
import com.civicflow.queue.enums.QueueTicketStatus;
import com.civicflow.queue.mapper.QueueNotificationOutboxMapper;
import com.civicflow.queue.mapper.QueueTicketMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    private final Clock clock;
    private final Duration budget;
    private final LongSupplier ticker;

    @Autowired
    public QueueNotificationPublisher(
            QueueNotificationOutboxMapper outbox,
            QueueTicketMapper tickets,
            RabbitTemplate rabbit,
            Clock clock,
            @Value("${civicflow.queue.notification-batch-budget:PT5S}") Duration budget) {
        this(outbox, tickets, rabbit, clock, budget, System::nanoTime);
    }

    public QueueNotificationPublisher(
            QueueNotificationOutboxMapper outbox,
            QueueTicketMapper tickets,
            RabbitTemplate rabbit,
            Clock clock,
            Duration budget,
            LongSupplier ticker) {
        if (budget.isNegative() || budget.isZero())
            throw new IllegalArgumentException("Publish budget must be positive");
        this.outbox = outbox;
        this.tickets = tickets;
        this.rabbit = rabbit;
        this.clock = clock;
        this.budget = budget;
        this.ticker = ticker;
    }

    @Scheduled(
            scheduler = "notificationScheduler",
            fixedDelayString = "${civicflow.queue.notification-publish-delay-ms:1000}")
    public void publish() {
        long started = ticker.getAsLong();
        for (OutboxEventEntity candidate : outbox.due(50)) {
            if (ticker.getAsLong() - started >= budget.toNanos()
                    || Thread.currentThread().isInterrupted()) break;
            if (outbox.claimNotification(candidate.getId(), candidate.getAttempts()) != 1) continue;
            OutboxEventEntity entry = candidate;
            entry.setAttempts(candidate.getAttempts() + 1);
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
                CorrelationData correlation =
                        new CorrelationData(entry.getEventId() + ":" + entry.getAttempts());
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
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                update(entry, OutboxStatus.FAILED, "PUBLISH_UNKNOWN");
                break;
            } catch (Exception exception) {
                update(entry, OutboxStatus.FAILED, "PUBLISH_UNKNOWN");
            }
        }
    }

    private void update(OutboxEventEntity entry, OutboxStatus status, String code) {
        int changed =
                outbox.finishNotification(
                        entry.getId(),
                        entry.getAttempts(),
                        status.name(),
                        status == OutboxStatus.PUBLISHED ? clock.instant() : null,
                        status == OutboxStatus.PUBLISHED ? null : code);
        if (changed == 0) {
            // Lease takeover won; this attempt must not overwrite the newer publisher.
            return;
        }
    }
}
