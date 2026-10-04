package com.civicflow.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.civicflow.appointment.client.ResourceSlotClient;
import com.civicflow.appointment.client.ResourceSlotSnapshot;
import com.civicflow.appointment.config.AppointmentProperties;
import com.civicflow.appointment.entity.AppointmentOrderEntity;
import com.civicflow.appointment.entity.OutboxEventEntity;
import com.civicflow.appointment.enums.AppointmentStatus;
import com.civicflow.appointment.mapper.AppointmentOperationLogMapper;
import com.civicflow.appointment.mapper.AppointmentOrderMapper;
import com.civicflow.appointment.mapper.OutboxEventMapper;
import com.civicflow.appointment.service.impl.CheckInTokenServiceImpl;
import com.civicflow.common.api.ApiResponse;
import com.civicflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class CheckInTokenServiceTest {
    private final AppointmentOrderMapper orders = Mockito.mock(AppointmentOrderMapper.class);
    private final ResourceSlotClient slots = Mockito.mock(ResourceSlotClient.class);
    private final OutboxEventMapper outbox = Mockito.mock(OutboxEventMapper.class);
    private final AppointmentOperationLogMapper logs =
            Mockito.mock(AppointmentOperationLogMapper.class);
    private final AppointmentProperties properties = new AppointmentProperties();
    private final Instant now = Instant.parse("2026-09-25T00:30:00Z");
    private AppointmentOrderEntity order;

    @BeforeEach
    void setup() {
        properties
                .getCheckIn()
                .setSigningKeyBase64(Base64.getEncoder().encodeToString(new byte[32]));
        order = new AppointmentOrderEntity();
        order.setId(101L);
        order.setUserId(201L);
        order.setOutletId(301L);
        order.setItemId(401L);
        order.setSlotId(501L);
        order.setServiceDate(LocalDate.of(2026, 9, 25));
        order.setStatus(AppointmentStatus.CONFIRMED);
        when(orders.selectOwnedById(101L, 201L)).thenReturn(order);
        when(orders.setQr(eq(101L), eq(201L), any(), any(), any()))
                .thenAnswer(
                        invocation -> {
                            order.setQrNonceHash(invocation.getArgument(2));
                            order.setQrExpiresAt(invocation.getArgument(3));
                            return 1;
                        });
        when(orders.claimCheckIn(eq(101L), eq(201L), eq(301L), any(), any()))
                .thenAnswer(
                        invocation -> {
                            order.setStatus(AppointmentStatus.CHECKED_IN);
                            order.setCheckinClaimId(invocation.getArgument(4));
                            order.setCheckedInAt(now);
                            return 1;
                        });
        when(slots.getSnapshot(501L)).thenReturn(ApiResponse.success(snapshot(), "test"));
    }

    @Test
    void signedTokenClaimsOnceAndReusesClaim() {
        var service = service(now);
        String token = service.issue(201L, 101L, 301L).token();
        var first = service.claim(token, 201L, 301L, UUID.randomUUID().toString(), "trace-test");
        var second = service.claim(token, 201L, 301L, UUID.randomUUID().toString(), "trace-test");
        assertEquals(first.claimId(), second.claimId());
        assertEquals("CHECKED_IN", first.status());
        ArgumentCaptor<OutboxEventEntity> event = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outbox).insert(event.capture());
        assertTrue(event.getValue().getPayloadJson().contains(first.claimId()));
        assertTrue(!event.getValue().getPayloadJson().contains(token));
    }

    @Test
    void rejectsTamperingWrongOwnerAndOutletAndExpiry() {
        var service = service(now);
        String token = service.issue(201L, 101L, 301L).token();
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertThrows(
                BusinessException.class,
                () -> service.claim(tampered, 201L, 301L, UUID.randomUUID().toString(), "trace"));
        assertThrows(
                BusinessException.class,
                () -> service.claim(token, 202L, 301L, UUID.randomUUID().toString(), "trace"));
        assertThrows(
                BusinessException.class,
                () -> service.claim(token, 201L, 302L, UUID.randomUUID().toString(), "trace"));
        assertThrows(
                BusinessException.class,
                () ->
                        service(now.plusSeconds(121))
                                .claim(token, 201L, 301L, UUID.randomUUID().toString(), "trace"));
    }

    @Test
    void rejectsOutsideWindow() {
        assertThrows(
                BusinessException.class,
                () -> service(now.minusSeconds(3600)).issue(201L, 101L, 301L));
    }

    @Test
    void reissueInvalidatesPreviousNonce() {
        var service = service(now);
        String oldToken = service.issue(201L, 101L, 301L).token();
        String currentToken = service.issue(201L, 101L, 301L).token();
        assertThrows(
                BusinessException.class,
                () -> service.claim(oldToken, 201L, 301L, UUID.randomUUID().toString(), "trace"));
        assertEquals(
                "CHECKED_IN",
                service.claim(currentToken, 201L, 301L, UUID.randomUUID().toString(), "trace")
                        .status());
    }

    private CheckInTokenServiceImpl service(Instant instant) {
        return new CheckInTokenServiceImpl(
                orders,
                slots,
                properties,
                Clock.fixed(instant, ZoneOffset.UTC),
                outbox,
                new ObjectMapper().findAndRegisterModules(),
                logs);
    }

    private ResourceSlotSnapshot snapshot() {
        return new ResourceSlotSnapshot(
                "501",
                "301",
                "401",
                "Outlet",
                "Item",
                LocalDate.of(2026, 9, 25),
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                5,
                now.minusSeconds(3600),
                now.minusSeconds(1800),
                now.plusSeconds(1800),
                now.plusSeconds(5400),
                "OPEN",
                1);
    }
}
