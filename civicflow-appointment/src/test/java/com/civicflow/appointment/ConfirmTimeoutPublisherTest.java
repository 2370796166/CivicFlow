package com.civicflow.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.civicflow.appointment.config.AppointmentProperties;
import com.civicflow.appointment.entity.OutboxEventEntity;
import com.civicflow.appointment.enums.OutboxStatus;
import com.civicflow.appointment.event.AppointmentMessaging;
import com.civicflow.appointment.event.ConfirmTimeoutEvent;
import com.civicflow.appointment.event.ConfirmTimeoutPublisher;
import com.civicflow.appointment.mapper.OutboxEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class ConfirmTimeoutPublisherTest {
    @Test
    void claimedOutboxPublishesPersistentTtlMessageAndRecordsAck() throws Exception {
        Instant now = Instant.parse("2026-09-23T10:00:00Z");
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        ConfirmTimeoutEvent event =
                new ConfirmTimeoutEvent(
                        1,
                        UUID.randomUUID().toString(),
                        AppointmentMessaging.TIMEOUT_EVENT_TYPE,
                        1,
                        now,
                        "civicflow-appointment",
                        "trace",
                        "reservation",
                        "parent",
                        "timeout:reservation",
                        new ConfirmTimeoutEvent.Payload(
                                "reservation", "100", now.plusSeconds(300)));
        OutboxEventEntity entry = new OutboxEventEntity();
        entry.setId(1L);
        entry.setEventId(event.eventId());
        entry.setPayloadJson(json.writeValueAsString(event));
        entry.setAttempts(0);
        OutboxEventMapper mapper = mock(OutboxEventMapper.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        when(mapper.selectDue(anyInt())).thenReturn(List.of(entry));
        when(mapper.claim(1L)).thenReturn(1);
        doAnswer(
                        invocation -> {
                            CorrelationData correlation = invocation.getArgument(3);
                            correlation
                                    .getFuture()
                                    .complete(new CorrelationData.Confirm(true, null));
                            return null;
                        })
                .when(rabbit)
                .send(
                        eq(AppointmentMessaging.TIMEOUT_EXCHANGE),
                        eq(AppointmentMessaging.TIMEOUT_SCHEDULE_ROUTING_KEY),
                        any(Message.class),
                        any(CorrelationData.class));
        ConfirmTimeoutPublisher publisher =
                new ConfirmTimeoutPublisher(
                        mapper,
                        rabbit,
                        json,
                        new AppointmentProperties(),
                        Clock.fixed(now, ZoneOffset.UTC));

        publisher.publishDue();

        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(rabbit)
                .send(
                        eq(AppointmentMessaging.TIMEOUT_EXCHANGE),
                        eq(AppointmentMessaging.TIMEOUT_SCHEDULE_ROUTING_KEY),
                        sent.capture(),
                        any(CorrelationData.class));
        assertEquals("300000", sent.getValue().getMessageProperties().getExpiration());
        ArgumentCaptor<OutboxEventEntity> update = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(mapper).update(update.capture(), any());
        assertEquals(OutboxStatus.PUBLISHED, update.getValue().getStatus());
    }
}
