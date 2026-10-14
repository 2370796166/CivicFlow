package com.civicflow.appointment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.civicflow.appointment.event.AppointmentMessaging;
import com.civicflow.appointment.event.SlotChangeListener;
import com.civicflow.appointment.event.SlotMessaging;
import com.civicflow.appointment.service.SlotStockSynchronizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class SlotChangeListenerTest {
    private final SlotStockSynchronizationService stock =
            mock(SlotStockSynchronizationService.class);
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final Channel channel = mock(Channel.class);
    private final SlotChangeListener listener =
            new SlotChangeListener(new ObjectMapper(), stock, rabbit);

    private Message message(boolean valid) {
        var properties = new MessageProperties();
        properties.setDeliveryTag(7);
        String body =
                valid
                        ? "{\"eventId\":\"b0cc86f5-0417-4e89-bf1c-75111ab4a14a\",\"producer\":\"civicflow-resource\",\"eventType\":\"resource.slot.changed\",\"eventVersion\":1,\"payload\":{\"slotId\":\"1\"}}"
                        : "invalid";
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }

    @Test
    void successfulAndRepeatedNoticesAreAcknowledged() throws Exception {
        listener.consume(message(true), channel);
        verify(stock).synchronize(1);
        verify(channel).basicAck(7, false);
    }

    @Test
    void temporaryFailureUsesDurableDelayedRetryWithoutAcknowledging() throws Exception {
        doThrow(new IllegalStateException("Redis unavailable")).when(stock).synchronize(1);
        listener.consume(message(true), channel);
        verify(channel).basicNack(7, false, false);
        verify(channel, never()).basicAck(7, false);
    }

    @Test
    void poisonNoticeIsAcknowledgedOnlyAfterConfirmedQuarantine() throws Exception {
        doAnswer(
                        invocation -> {
                            CorrelationData confirmation = invocation.getArgument(3);
                            confirmation
                                    .getFuture()
                                    .complete(new CorrelationData.Confirm(true, null));
                            return null;
                        })
                .when(rabbit)
                .send(
                        eq(AppointmentMessaging.DEAD_LETTER_EXCHANGE),
                        eq(SlotMessaging.DEAD_ROUTING),
                        any(Message.class),
                        any(CorrelationData.class));
        listener.consume(message(false), channel);
        verify(channel).basicAck(7, false);
        verify(stock, never()).synchronize(1);
    }

    @Test
    void unconfirmedQuarantineRetainsTheOriginalNoticeForRetry() throws Exception {
        doAnswer(
                        invocation -> {
                            CorrelationData confirmation = invocation.getArgument(3);
                            confirmation
                                    .getFuture()
                                    .complete(new CorrelationData.Confirm(false, "synthetic nack"));
                            return null;
                        })
                .when(rabbit)
                .send(
                        eq(AppointmentMessaging.DEAD_LETTER_EXCHANGE),
                        eq(SlotMessaging.DEAD_ROUTING),
                        any(Message.class),
                        any(CorrelationData.class));
        listener.consume(message(false), channel);
        verify(channel).basicNack(7, false, false);
        verify(channel, never()).basicAck(7, false);
    }
}
