package com.civicflow.resource.event;

import com.civicflow.resource.config.SlotMessagingConfig;
import com.civicflow.resource.mapper.ResourceSlotOutboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Durable, ordered per-slot publication; attempts fence late confirmations after lease takeover.
 */
@Component
@ConditionalOnProperty(
        name = "civicflow.resource.slot-publishing-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SlotChangePublisher {
    private final ResourceSlotOutboxMapper outbox;
    private final RabbitTemplate rabbit;
    private final ObjectMapper json;
    private final Clock clock;

    public SlotChangePublisher(
            ResourceSlotOutboxMapper outbox,
            RabbitTemplate rabbit,
            ObjectMapper json,
            Clock clock) {
        this.outbox = outbox;
        this.rabbit = rabbit;
        this.json = json;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${civicflow.resource.slot-publish-delay:3s}")
    public void publishDue() {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        for (var entry : outbox.due(clock.instant(), 20)) {
            if (System.nanoTime() >= deadline) break;
            var now = clock.instant();
            if (outbox.claim(entry.getId(), entry.getAttempts(), now, now.plusSeconds(60)) != 1)
                continue;
            boolean acknowledged = false;
            try {
                var body = json.readTree(entry.getPayloadJson());
                if (body.isTextual()) body = json.readTree(body.textValue());
                var props = new MessageProperties();
                props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                props.setMessageId(entry.getEventId());
                var confirmation =
                        new CorrelationData(entry.getEventId() + ":" + (entry.getAttempts() + 1));
                rabbit.send(
                        SlotMessagingConfig.EXCHANGE,
                        entry.getRoutingKey(),
                        new Message(json.writeValueAsBytes(body), props),
                        confirmation);
                acknowledged =
                        confirmation.getFuture().get(3, TimeUnit.SECONDS).isAck()
                                && confirmation.getReturned() == null;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (Exception exception) {
                // Retry the same eventId; never log the body, headers or underlying exception.
            }
            now = clock.instant();
            outbox.finish(
                    entry.getId(),
                    entry.getAttempts() + 1,
                    acknowledged ? "PUBLISHED" : "FAILED",
                    acknowledged ? now : null,
                    now.plusSeconds(10),
                    acknowledged ? null : "PUBLISH_UNCONFIRMED",
                    now);
            if (Thread.currentThread().isInterrupted()) break;
        }
    }
}
