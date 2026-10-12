package com.civicflow.notification;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.civicflow.notification.entity.NotificationRecordEntity;
import com.civicflow.notification.event.NotificationEvent;
import com.civicflow.notification.mapper.NotificationPreferenceMapper;
import com.civicflow.notification.mapper.NotificationRecordMapper;
import com.civicflow.notification.service.DeliveryChannel;
import com.civicflow.notification.service.NotificationProvider;
import com.civicflow.notification.service.NotificationStatus;
import com.civicflow.notification.service.impl.NotificationDeliveryService;
import com.civicflow.notification.service.impl.NotificationServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {
    @Test
    void duplicateDeliveredEventHasNoSecondProviderSideEffect() {
        Fixture fixture = new Fixture();
        NotificationRecordEntity record = record(NotificationStatus.SENT, 1);
        when(fixture.delivery.prepare(fixture.event, fixture.json)).thenReturn(record);
        fixture.service.consume(fixture.event, fixture.json);
        verify(fixture.provider, never()).send(record);
        verify(fixture.delivery, never()).claim(record.getId());
    }

    @Test
    void thirdProviderFailureBecomesDeadAndCanReachBrokerDlq() {
        Fixture fixture = new Fixture();
        NotificationRecordEntity record = record(NotificationStatus.FAILED, 2);
        when(fixture.delivery.prepare(fixture.event, fixture.json)).thenReturn(record);
        when(fixture.delivery.claim(record.getId())).thenReturn(true);
        doThrow(new IllegalStateException("provider unavailable"))
                .when(fixture.provider)
                .send(record);
        assertThatThrownBy(() -> fixture.service.consume(fixture.event, fixture.json))
                .isInstanceOf(IllegalStateException.class);
        verify(fixture.delivery).failed(record.getId(), true);
    }

    private static NotificationRecordEntity record(NotificationStatus status, int attempts) {
        NotificationRecordEntity record = new NotificationRecordEntity();
        record.setId(42L);
        record.setStatus(status.name());
        record.setAttempts(attempts);
        return record;
    }

    private static final class Fixture {
        final NotificationDeliveryService delivery = mock(NotificationDeliveryService.class);
        final NotificationRecordMapper records = mock(NotificationRecordMapper.class);
        final NotificationPreferenceMapper preferences = mock(NotificationPreferenceMapper.class);
        final NotificationProvider provider = mock(NotificationProvider.class);
        final NotificationEvent event =
                new NotificationEvent(
                        1,
                        UUID.randomUUID().toString(),
                        "appointment.notification.created",
                        1,
                        Instant.now(),
                        "civicflow-appointment",
                        "trace",
                        "42",
                        "reservation",
                        new NotificationEvent.Payload(
                                "12",
                                "42",
                                "APPOINTMENT_CREATED",
                                1,
                                Map.of("appointmentId", "42"),
                                true));
        final String json = "{}";
        final NotificationServiceImpl service;

        Fixture() {
            when(provider.channel()).thenReturn(DeliveryChannel.IN_APP);
            service =
                    new NotificationServiceImpl(
                            delivery, records, preferences, List.of(provider), new ObjectMapper());
        }
    }
}
