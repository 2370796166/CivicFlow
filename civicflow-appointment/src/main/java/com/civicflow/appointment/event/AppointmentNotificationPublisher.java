package com.civicflow.appointment.event;

import com.civicflow.appointment.entity.AppointmentOrderEntity;
import com.civicflow.appointment.entity.OutboxEventEntity;
import com.civicflow.appointment.enums.AppointmentStatus;
import com.civicflow.appointment.enums.OutboxStatus;
import com.civicflow.appointment.mapper.AppointmentOrderMapper;
import com.civicflow.appointment.mapper.OutboxEventMapper;
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
        name = "civicflow.appointment.notification-publisher-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AppointmentNotificationPublisher {
    private final OutboxEventMapper outbox;
    private final AppointmentOrderMapper orders;
    private final RabbitTemplate rabbit;
    private final Clock clock;
    private final Duration budget;
    private final LongSupplier ticker;

    @Autowired
    public AppointmentNotificationPublisher(
            OutboxEventMapper outbox,
            AppointmentOrderMapper orders,
            RabbitTemplate rabbit,
            Clock clock,
            @Value("${civicflow.appointment.notification-batch-budget:PT5S}") Duration budget) {
        this(outbox, orders, rabbit, clock, budget, System::nanoTime);
    }

    public AppointmentNotificationPublisher(
            OutboxEventMapper outbox,
            AppointmentOrderMapper orders,
            RabbitTemplate rabbit,
            Clock clock,
            Duration budget,
            LongSupplier ticker) {
        if (budget.isNegative() || budget.isZero())
            throw new IllegalArgumentException("Publish budget must be positive");
        this.outbox = outbox;
        this.orders = orders;
        this.rabbit = rabbit;
        this.clock = clock;
        this.budget = budget;
        this.ticker = ticker;
    }

    @Scheduled(
            scheduler = "notificationScheduler",
            fixedDelayString = "${civicflow.appointment.notification-publish-delay-ms:1000}")
    public void publish() {
        long started = ticker.getAsLong();
        for (OutboxEventEntity candidate : outbox.selectDueNotification(50)) {
            if (ticker.getAsLong() - started >= budget.toNanos()
                    || Thread.currentThread().isInterrupted()) break;
            if (outbox.claimNotification(candidate.getId(), candidate.getAttempts()) != 1) continue;
            OutboxEventEntity entry = candidate;
            entry.setAttempts(candidate.getAttempts() + 1);
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
                CorrelationData correlation =
                        new CorrelationData(entry.getEventId() + ":" + entry.getAttempts());
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
