package com.civicflow.appointment.event;

import com.civicflow.appointment.error.AppointmentErrorCode;
import com.civicflow.appointment.service.SlotStockSynchronizationService;
import com.civicflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class SlotChangeListener {
    private final ObjectMapper json;
    private final SlotStockSynchronizationService stock;
    private final RabbitTemplate rabbit;

    public SlotChangeListener(
            ObjectMapper json, SlotStockSynchronizationService stock, RabbitTemplate rabbit) {
        this.json = json;
        this.stock = stock;
        this.rabbit = rabbit;
    }

    @RabbitListener(queues = SlotMessaging.QUEUE, containerFactory = "slotListenerContainerFactory")
    public void consume(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        long slotId;
        try {
            var event = json.readTree(message.getBody());
            UUID.fromString(event.path("eventId").asText());
            if (!"resource.slot.changed".equals(event.path("eventType").asText())
                    || event.path("eventVersion").asInt() != 1
                    || !"civicflow-resource".equals(event.path("producer").asText()))
                throw new IllegalArgumentException("Invalid slot event");
            slotId = Long.parseLong(event.path("payload").path("slotId").asText());
            if (slotId <= 0) throw new IllegalArgumentException("Invalid slot identifier");
        } catch (Exception invalid) {
            quarantine(message, channel, tag);
            return;
        }
        try {
            // Fetch the authoritative current snapshot; old and reordered notices are safe to
            // replay.
            stock.synchronize(slotId);
            channel.basicAck(tag, false);
        } catch (BusinessException exception) {
            if (exception.errorCode() == AppointmentErrorCode.STOCK_REBUILD_UNSAFE
                    || exception.errorCode() == AppointmentErrorCode.STOCK_INVARIANT_BROKEN)
                quarantine(message, channel, tag);
            else channel.basicNack(tag, false, false);
        } catch (RuntimeException exception) {
            channel.basicNack(tag, false, false);
        }
    }

    private void quarantine(Message message, Channel channel, long tag) throws IOException {
        try {
            var confirmation = new CorrelationData();
            rabbit.send(
                    AppointmentMessaging.DEAD_LETTER_EXCHANGE,
                    SlotMessaging.DEAD_ROUTING,
                    message,
                    confirmation);
            if (!confirmation.getFuture().get(3, TimeUnit.SECONDS).isAck()
                    || confirmation.getReturned() != null)
                throw new IllegalStateException("Quarantine not confirmed");
            channel.basicAck(tag, false);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            channel.basicNack(tag, false, false);
        } catch (Exception exception) {
            channel.basicNack(tag, false, false);
        }
    }
}
