package com.civicflow.resource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.civicflow.resource.entity.ResourceSlotOutboxEntity;
import com.civicflow.resource.event.SlotChangePublisher;
import com.civicflow.resource.mapper.ResourceSlotOutboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class SlotChangePublisherTest {
    private final ResourceSlotOutboxMapper outbox = mock(ResourceSlotOutboxMapper.class);
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final Instant now = Instant.parse("2026-10-14T00:00:00Z");
    private final SlotChangePublisher publisher =
            new SlotChangePublisher(
                    outbox, rabbit, new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC));

    private void entry(boolean claimed) {
        var row = new ResourceSlotOutboxEntity();
        row.setId(1L);
        row.setAttempts(3);
        row.setEventId("b0cc86f5-0417-4e89-bf1c-75111ab4a14a");
        row.setRoutingKey("resource.slot.changed.v1");
        row.setPayloadJson("{\"eventId\":\"b0cc86f5-0417-4e89-bf1c-75111ab4a14a\"}");
        when(outbox.due(now, 20)).thenReturn(List.of(row));
        when(outbox.claim(eq(1L), eq(3), eq(now), any())).thenReturn(claimed ? 1 : 0);
    }

    @Test
    void confirmsTheSameEventAndFencesCompletionByAttempt() {
        entry(true);
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
                        eq("cf.resource.x"),
                        eq("resource.slot.changed.v1"),
                        any(Message.class),
                        any(CorrelationData.class));
        publisher.publishDue();
        verify(outbox)
                .finish(
                        eq(1L),
                        eq(4),
                        eq("PUBLISHED"),
                        eq(now),
                        eq(now.plusSeconds(10)),
                        isNull(),
                        eq(now));
    }

    @Test
    void failedClaimDoesNotPublish() {
        entry(false);
        publisher.publishDue();
        verify(rabbit, never())
                .send(
                        any(String.class),
                        any(String.class),
                        any(Message.class),
                        any(CorrelationData.class));
    }
}
