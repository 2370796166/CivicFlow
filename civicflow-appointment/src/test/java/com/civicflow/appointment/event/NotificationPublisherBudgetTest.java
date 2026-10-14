package com.civicflow.appointment.event;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.civicflow.appointment.entity.OutboxEventEntity;
import com.civicflow.appointment.mapper.AppointmentOrderMapper;
import com.civicflow.appointment.mapper.OutboxEventMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class NotificationPublisherBudgetTest {
    @Test
    void batchStopsAtBudgetAndCompletionUsesClaimedAttempt() {
        var mapper = mock(OutboxEventMapper.class);
        var rabbit = mock(RabbitTemplate.class);
        var ticker = new AtomicLong();
        OutboxEventEntity first = entry(1), second = entry(2);
        when(mapper.selectDueNotification(50)).thenReturn(List.of(first, second));
        when(mapper.claimNotification(1, 0)).thenReturn(1);
        doAnswer(
                        call -> {
                            CorrelationData correlation = call.getArgument(3);
                            assertThat(correlation.getId()).isEqualTo("event-1:1");
                            correlation
                                    .getFuture()
                                    .complete(new CorrelationData.Confirm(true, null));
                            ticker.set(Duration.ofSeconds(6).toNanos());
                            return null;
                        })
                .when(rabbit)
                .send(
                        eq(AppointmentMessaging.APPOINTMENT_EXCHANGE),
                        anyString(),
                        any(Message.class),
                        any(CorrelationData.class));
        new AppointmentNotificationPublisher(
                        mapper,
                        mock(AppointmentOrderMapper.class),
                        rabbit,
                        Clock.systemUTC(),
                        Duration.ofSeconds(5),
                        ticker::get)
                .publish();
        verify(mapper).finishNotification(eq(1L), eq(1), eq("PUBLISHED"), notNull(), isNull());
        verify(mapper, never()).claimNotification(eq(2L), anyInt());
    }

    private static OutboxEventEntity entry(long id) {
        var entry = new OutboxEventEntity();
        entry.setId(id);
        entry.setAttempts(0);
        entry.setEventId("event-" + id);
        entry.setEventType("appointment.notification.created");
        entry.setRoutingKey("test.v1");
        entry.setPayloadJson("{}");
        return entry;
    }
}
