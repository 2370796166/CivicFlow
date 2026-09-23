package com.civicflow.appointment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.civicflow.appointment.config.AppointmentProperties;
import com.civicflow.appointment.entity.ActiveBookingGuardEntity;
import com.civicflow.appointment.entity.AppointmentOperationLogEntity;
import com.civicflow.appointment.entity.AppointmentOrderEntity;
import com.civicflow.appointment.entity.AppointmentReservationRequestEntity;
import com.civicflow.appointment.entity.MessageConsumeRecordEntity;
import com.civicflow.appointment.entity.OutboxEventEntity;
import com.civicflow.appointment.enums.AppointmentActorType;
import com.civicflow.appointment.enums.AppointmentOperation;
import com.civicflow.appointment.enums.AppointmentStatus;
import com.civicflow.appointment.enums.MessageConsumeStatus;
import com.civicflow.appointment.enums.OutboxStatus;
import com.civicflow.appointment.enums.ReservationRequestStatus;
import com.civicflow.appointment.error.AppointmentErrorCode;
import com.civicflow.appointment.event.AppointmentMessaging;
import com.civicflow.appointment.event.ConfirmTimeoutEvent;
import com.civicflow.appointment.event.PermanentReservationMessageException;
import com.civicflow.appointment.event.ReservationRequestedEvent;
import com.civicflow.appointment.mapper.ActiveBookingGuardMapper;
import com.civicflow.appointment.mapper.AppointmentOperationLogMapper;
import com.civicflow.appointment.mapper.AppointmentOrderMapper;
import com.civicflow.appointment.mapper.AppointmentReservationRequestMapper;
import com.civicflow.appointment.mapper.MessageConsumeRecordMapper;
import com.civicflow.appointment.mapper.OutboxEventMapper;
import com.civicflow.appointment.service.AppointmentCreationService;
import com.civicflow.appointment.service.ReservationCreationRejectedException;
import com.civicflow.appointment.support.Digests;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentCreationServiceImpl implements AppointmentCreationService {
    private final AppointmentOrderMapper orderMapper;
    private final ActiveBookingGuardMapper guardMapper;
    private final AppointmentOperationLogMapper logMapper;
    private final MessageConsumeRecordMapper consumeRecordMapper;
    private final AppointmentReservationRequestMapper requestMapper;
    private final AppointmentProperties properties;
    private final Clock clock;
    private final OutboxEventMapper outboxMapper;
    private final ObjectMapper objectMapper;

    public AppointmentCreationServiceImpl(
            AppointmentOrderMapper orderMapper,
            ActiveBookingGuardMapper guardMapper,
            AppointmentOperationLogMapper logMapper,
            MessageConsumeRecordMapper consumeRecordMapper,
            AppointmentReservationRequestMapper requestMapper,
            AppointmentProperties properties,
            Clock clock,
            OutboxEventMapper outboxMapper,
            ObjectMapper objectMapper) {
        this.orderMapper = orderMapper;
        this.guardMapper = guardMapper;
        this.logMapper = logMapper;
        this.consumeRecordMapper = consumeRecordMapper;
        this.requestMapper = requestMapper;
        this.properties = properties;
        this.clock = clock;
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public CreationResult create(ReservationRequestedEvent event, byte[] payloadHash) {
        validateEnvelope(event);
        ReservationRequestedEvent.Payload payload = event.payload();
        AppointmentReservationRequestEntity request = findRequest(payload.reservationId());
        if (request == null) {
            throw new PermanentReservationMessageException(
                    "Reservation request does not exist for event " + event.eventId());
        }
        validateMatchesRequest(event, request);
        MessageConsumeRecordEntity consumed = findConsumeRecord(event.eventId());
        AppointmentOrderEntity existing =
                orderMapper.selectByReservationId(payload.reservationId());
        if (consumed != null || existing != null) {
            if (consumed == null
                    || !Digests.equal(consumed.getPayloadHash(), payloadHash)
                    || existing == null
                    || !existing.getUserId().toString().equals(payload.userId())
                    || !existing.getSlotId().toString().equals(payload.slotId())) {
                throw new PermanentReservationMessageException(
                        "Reservation event idempotency payload conflict");
            }
            return new CreationResult(request, existing.getId(), true);
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        if (!now.isBefore(payload.closeAt())) {
            throw new ReservationCreationRejectedException(
                    request,
                    AppointmentErrorCode.SLOT_NOT_OPEN.code(),
                    "Slot closed before appointment creation");
        }

        AppointmentOrderEntity order = new AppointmentOrderEntity();
        order.setReservationId(payload.reservationId());
        order.setUserId(parsePositive(payload.userId(), "userId"));
        order.setSlotId(parsePositive(payload.slotId(), "slotId"));
        order.setOutletId(parsePositive(payload.outletId(), "outletId"));
        order.setItemId(parsePositive(payload.itemId(), "itemId"));
        order.setServiceDate(payload.serviceDate());
        order.setSlotStartTime(payload.slotStartTime());
        order.setSlotEndTime(payload.slotEndTime());
        order.setOutletNameSnapshot(payload.outletName());
        order.setItemNameSnapshot(payload.itemName());
        order.setStatus(AppointmentStatus.PENDING_CONFIRM);
        order.setConfirmDeadline(now.plus(properties.getReservation().getConfirmDeadline()));
        order.setVersion(0);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orderMapper.insert(order);

        ActiveBookingGuardEntity guard = new ActiveBookingGuardEntity();
        guard.setUserId(order.getUserId());
        guard.setItemId(order.getItemId());
        guard.setServiceDate(order.getServiceDate());
        guard.setReservationId(order.getReservationId());
        guard.setAppointmentId(order.getId());
        guardMapper.insert(guard);

        AppointmentOperationLogEntity log = new AppointmentOperationLogEntity();
        log.setAppointmentId(order.getId());
        log.setReservationId(order.getReservationId());
        log.setActorType(AppointmentActorType.SERVICE);
        log.setActorId(0L);
        log.setOperation(AppointmentOperation.CREATE);
        log.setToStatus(AppointmentStatus.PENDING_CONFIRM);
        log.setRequestId(event.traceId());
        log.setOccurredAt(now);
        log.setDetailJson(
                "{\"reason\":\"RESERVATION_PERSISTED\",\"eventId\":\"" + event.eventId() + "\"}");
        logMapper.insert(log);

        ConfirmTimeoutEvent timeout =
                new ConfirmTimeoutEvent(
                        1,
                        UUID.randomUUID().toString(),
                        AppointmentMessaging.TIMEOUT_EVENT_TYPE,
                        1,
                        now,
                        "civicflow-appointment",
                        event.traceId(),
                        order.getReservationId(),
                        event.eventId(),
                        "timeout:" + order.getReservationId(),
                        new ConfirmTimeoutEvent.Payload(
                                order.getReservationId(),
                                order.getId().toString(),
                                order.getConfirmDeadline()));
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setEventId(timeout.eventId());
        outbox.setAggregateType("appointment_order");
        outbox.setAggregateId(order.getId().toString());
        outbox.setEventType(AppointmentMessaging.TIMEOUT_EVENT_TYPE);
        outbox.setEventVersion(1);
        outbox.setRoutingKey(AppointmentMessaging.TIMEOUT_SCHEDULE_ROUTING_KEY);
        try {
            outbox.setPayloadJson(objectMapper.writeValueAsString(timeout));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Cannot serialize confirmation timeout event", exception);
        }
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setAttempts(0);
        outbox.setNextAttemptAt(now);
        outboxMapper.insert(outbox);

        MessageConsumeRecordEntity record = new MessageConsumeRecordEntity();
        record.setConsumerName(AppointmentMessaging.RESERVATION_CREATE_CONSUMER);
        record.setIdempotencyKey(payload.reservationId());
        record.setEventId(event.eventId());
        record.setPayloadHash(payloadHash);
        record.setStatus(MessageConsumeStatus.PROCESSED);
        record.setFirstSeenAt(now);
        record.setProcessedAt(now);
        consumeRecordMapper.insert(record);

        request.setStatus(ReservationRequestStatus.PERSISTED);
        request.setFailureCode(null);
        request.setLastErrorCode(null);
        requestMapper.updateById(request);
        return new CreationResult(request, order.getId(), false);
    }

    @Override
    public AppointmentReservationRequestEntity findRequest(String reservationId) {
        return requestMapper.selectOne(
                Wrappers.<AppointmentReservationRequestEntity>lambdaQuery()
                        .eq(AppointmentReservationRequestEntity::getReservationId, reservationId));
    }

    @Override
    public Long findAppointmentId(String reservationId) {
        AppointmentOrderEntity order = orderMapper.selectByReservationId(reservationId);
        return order == null ? null : order.getId();
    }

    private MessageConsumeRecordEntity findConsumeRecord(String eventId) {
        return consumeRecordMapper.selectOne(
                Wrappers.<MessageConsumeRecordEntity>lambdaQuery()
                        .eq(
                                MessageConsumeRecordEntity::getConsumerName,
                                AppointmentMessaging.RESERVATION_CREATE_CONSUMER)
                        .eq(MessageConsumeRecordEntity::getEventId, eventId));
    }

    private static void validateEnvelope(ReservationRequestedEvent event) {
        if (event == null
                || event.schemaVersion() != ReservationRequestedEvent.SCHEMA_VERSION
                || event.eventVersion() != ReservationRequestedEvent.EVENT_VERSION
                || !ReservationRequestedEvent.EVENT_TYPE.equals(event.eventType())
                || !ReservationRequestedEvent.PRODUCER.equals(event.producer())
                || event.eventId() == null
                || event.occurredAt() == null
                || event.traceId() == null
                || event.traceId().isBlank()
                || event.payload() == null) {
            throw new PermanentReservationMessageException(
                    "Unsupported or incomplete reservation event envelope");
        }
        ReservationRequestedEvent.Payload payload = event.payload();
        if (!event.eventId().equals(payload.reservationId())
                || !event.eventId().equals(event.correlationId())
                || payload.serviceDate() == null
                || payload.slotStartTime() == null
                || payload.slotEndTime() == null
                || !payload.slotEndTime().isAfter(payload.slotStartTime())
                || payload.outletName() == null
                || payload.outletName().isBlank()
                || payload.itemName() == null
                || payload.itemName().isBlank()
                || payload.releaseAt() == null
                || payload.closeAt() == null
                || payload.reservedAt() == null
                || payload.reservationExpiresAt() == null
                || payload.slotConfigVersion() <= 0) {
            throw new PermanentReservationMessageException("Invalid reservation event payload");
        }
    }

    private static void validateMatchesRequest(
            ReservationRequestedEvent event, AppointmentReservationRequestEntity request) {
        ReservationRequestedEvent.Payload payload = event.payload();
        if (!request.getReservationId().equals(payload.reservationId())
                || !request.getTraceId().equals(event.traceId())
                || !request.getTraceId().equals(event.causationId())
                || !request.getReservedAt().equals(event.occurredAt())
                || !request.getUserId().toString().equals(payload.userId())
                || !request.getSlotId().toString().equals(payload.slotId())
                || !request.getOutletId().toString().equals(payload.outletId())
                || !request.getItemId().toString().equals(payload.itemId())
                || !request.getServiceDate().equals(payload.serviceDate())
                || !request.getSlotStartTime().equals(payload.slotStartTime())
                || !request.getSlotEndTime().equals(payload.slotEndTime())
                || !request.getOutletNameSnapshot().equals(payload.outletName())
                || !request.getItemNameSnapshot().equals(payload.itemName())
                || request.getTotalQuota() != payload.totalQuota()
                || !request.getReleaseAt().equals(payload.releaseAt())
                || !request.getCloseAt().equals(payload.closeAt())
                || !request.getSlotStatus().equals(payload.slotStatus())
                || request.getSlotConfigVersion() != payload.slotConfigVersion()
                || !request.getReservedAt().equals(payload.reservedAt())
                || !request.getReservationExpiresAt().equals(payload.reservationExpiresAt())) {
            throw new PermanentReservationMessageException(
                    "Reservation event does not match the trusted request snapshot");
        }
    }

    private static long parsePositive(String value, String field) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new NumberFormatException(field);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new PermanentReservationMessageException(
                    "Invalid identifier in reservation event: " + field, exception);
        }
    }
}
