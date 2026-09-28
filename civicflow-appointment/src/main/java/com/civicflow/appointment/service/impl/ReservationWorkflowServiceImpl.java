package com.civicflow.appointment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.civicflow.appointment.client.ResourceSlotClient;
import com.civicflow.appointment.client.ResourceSlotSnapshot;
import com.civicflow.appointment.config.AppointmentProperties;
import com.civicflow.appointment.dto.response.ReservationCreateResponse;
import com.civicflow.appointment.entity.AppointmentReservationRequestEntity;
import com.civicflow.appointment.enums.ReservationRequestStatus;
import com.civicflow.appointment.enums.StockReleaseReason;
import com.civicflow.appointment.error.AppointmentErrorCode;
import com.civicflow.appointment.event.ReservationEventPublisher;
import com.civicflow.appointment.event.ReservationRequestedEvent;
import com.civicflow.appointment.mapper.AppointmentReservationRequestMapper;
import com.civicflow.appointment.service.ReservationRedisStateService;
import com.civicflow.appointment.service.ReservationStockService;
import com.civicflow.appointment.service.ReservationWorkflowService;
import com.civicflow.appointment.service.RetryableStockCompensationException;
import com.civicflow.appointment.service.StockReleaseService;
import com.civicflow.appointment.support.Digests;
import com.civicflow.common.api.ApiResponse;
import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class ReservationWorkflowServiceImpl implements ReservationWorkflowService {
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    private final AppointmentReservationRequestMapper requestMapper;
    private final ResourceSlotClient resourceClient;
    private final ReservationStockService stockService;
    private final ReservationEventPublisher publisher;
    private final ReservationRedisStateService redisStateService;
    private final StockReleaseService stockReleaseService;
    private final AppointmentProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ReservationWorkflowServiceImpl(
            AppointmentReservationRequestMapper requestMapper,
            ResourceSlotClient resourceClient,
            ReservationStockService stockService,
            ReservationEventPublisher publisher,
            ReservationRedisStateService redisStateService,
            StockReleaseService stockReleaseService,
            AppointmentProperties properties,
            ObjectMapper objectMapper,
            Clock clock) {
        this.requestMapper = requestMapper;
        this.resourceClient = resourceClient;
        this.stockService = stockService;
        this.publisher = publisher;
        this.redisStateService = redisStateService;
        this.stockReleaseService = stockReleaseService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public CreateOutcome create(long userId, long slotId, String idempotencyKey, String traceId) {
        validate(userId, slotId, idempotencyKey, traceId);
        byte[] keyHash = Digests.sha256(idempotencyKey);
        byte[] payloadHash = Digests.sha256("slotId=" + slotId);
        AppointmentReservationRequestEntity request = findByIdempotency(userId, keyHash);
        if (request == null) {
            request = createRequest(userId, slotId, keyHash, payloadHash, traceId);
        } else if (!Digests.equal(request.getPayloadHash(), payloadHash)) {
            throw new BusinessException(CommonErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return progress(request);
    }

    @Override
    public void recover(long requestId) {
        AppointmentReservationRequestEntity request = requestMapper.selectById(requestId);
        if (request == null
                || request.getStatus() == ReservationRequestStatus.PERSISTED
                || request.getStatus() == ReservationRequestStatus.PUBLISHED
                || request.getStatus() == ReservationRequestStatus.FAILED) {
            return;
        }
        progress(request);
    }

    private CreateOutcome progress(AppointmentReservationRequestEntity request) {
        boolean previouslyUnknown = request.getStatus() == ReservationRequestStatus.PUBLISH_UNKNOWN;
        if (request.getStatus() == ReservationRequestStatus.PERSISTED
                || request.getStatus() == ReservationRequestStatus.PUBLISHED) {
            return creating(request, false);
        }
        if (request.getStatus() == ReservationRequestStatus.COMPENSATION_PENDING) {
            return compensatePublishFailure(request);
        }
        if (request.getStatus() == ReservationRequestStatus.FAILED) {
            return failed(request, true);
        }
        ReservationRequestedEvent event;
        if (request.getStatus() == ReservationRequestStatus.CREATED) {
            event = reserve(request);
            request = requestMapper.selectById(request.getId());
            if (request.getStatus() == ReservationRequestStatus.PERSISTED
                    || request.getStatus() == ReservationRequestStatus.PUBLISHED) {
                return creating(request, false);
            }
            previouslyUnknown = request.getStatus() == ReservationRequestStatus.PUBLISH_UNKNOWN;
        } else {
            event = readEvent(request);
        }
        ReservationEventPublisher.PublishResult publishResult = publisher.publish(event);
        request.setPublishAttempts(request.getPublishAttempts() + publishResult.attempts());
        request.setLastErrorCode(publishResult.errorCode());
        if (publishResult.outcome() == ReservationEventPublisher.Outcome.ACKNOWLEDGED) {
            request.setStatus(ReservationRequestStatus.PUBLISHED);
            request.setLastErrorCode(null);
            if (requestMapper.recordPublication(request) != 1) {
                request = requestMapper.selectById(request.getId());
            }
            try {
                if (!redisStateService.markPublished(request)) {
                    request.setLastErrorCode("REDIS_MARK_PUBLISHED_RECONCILE");
                    recordPublishMarkerFailure(request);
                }
            } catch (RuntimeException exception) {
                request.setLastErrorCode("REDIS_MARK_PUBLISHED_FAILED");
                recordPublishMarkerFailure(request);
            }
            return creating(request, false);
        }
        if (publishResult.outcome() == ReservationEventPublisher.Outcome.UNKNOWN
                || previouslyUnknown) {
            request.setStatus(ReservationRequestStatus.PUBLISH_UNKNOWN);
            request.setNextRecoveryAt(
                    clock.instant().plus(properties.getRecovery().getRetryDelay()));
            if (requestMapper.recordPublication(request) != 1) {
                request = requestMapper.selectById(request.getId());
                if (request.getStatus() == ReservationRequestStatus.PERSISTED
                        || request.getStatus() == ReservationRequestStatus.PUBLISHED) {
                    return creating(request, false);
                }
            }
            return creating(request, true);
        }
        return compensatePublishFailure(request);
    }

    private ReservationRequestedEvent reserve(AppointmentReservationRequestEntity request) {
        Instant reservedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        try {
            stockService.reserve(
                    ReservationRedisStateServiceImpl.snapshot(request),
                    request.getUserId(),
                    request.getReservationId());
        } catch (BusinessException exception) {
            request.setStatus(ReservationRequestStatus.FAILED);
            request.setFailureCode(exception.errorCode().code());
            request.setLastErrorCode(exception.errorCode().code());
            requestMapper.update(
                    request,
                    Wrappers.<AppointmentReservationRequestEntity>lambdaUpdate()
                            .eq(AppointmentReservationRequestEntity::getId, request.getId())
                            .eq(
                                    AppointmentReservationRequestEntity::getStatus,
                                    ReservationRequestStatus.CREATED));
            throw exception;
        }
        request.setReservedAt(reservedAt);
        request.setReservationExpiresAt(reservedAt.plus(properties.getStock().getReservationTtl()));
        ReservationRequestedEvent event = event(request);
        request.setEventJson(writeEvent(event));
        request.setStatus(ReservationRequestStatus.RESERVED);
        if (requestMapper.saveReservedEvent(request) == 1) {
            return event;
        }
        return readEvent(requestMapper.selectById(request.getId()));
    }

    private void recordPublishMarkerFailure(AppointmentReservationRequestEntity request) {
        // Diagnostics do not write an old entity snapshot back over consumer state or event data.
        requestMapper.update(
                null,
                Wrappers.<AppointmentReservationRequestEntity>lambdaUpdate()
                        .eq(AppointmentReservationRequestEntity::getId, request.getId())
                        .eq(
                                AppointmentReservationRequestEntity::getStatus,
                                ReservationRequestStatus.PUBLISHED)
                        .set(
                                AppointmentReservationRequestEntity::getLastErrorCode,
                                request.getLastErrorCode()));
    }

    private CreateOutcome compensatePublishFailure(AppointmentReservationRequestEntity request) {
        try {
            stockReleaseService.release(request, StockReleaseReason.PUBLISH_FAILED);
            request.setStatus(ReservationRequestStatus.FAILED);
            request.setFailureCode(AppointmentErrorCode.PUBLISH_FAILED.code());
        } catch (RetryableStockCompensationException exception) {
            request.setStatus(ReservationRequestStatus.COMPENSATION_PENDING);
            request.setFailureCode(AppointmentErrorCode.COMPENSATION_PENDING.code());
            request.setLastErrorCode(AppointmentErrorCode.COMPENSATION_PENDING.code());
            request.setNextRecoveryAt(
                    clock.instant().plus(properties.getRecovery().getRetryDelay()));
        }
        requestMapper.updateById(request);
        return failed(request, true);
    }

    private AppointmentReservationRequestEntity createRequest(
            long userId, long slotId, byte[] keyHash, byte[] payloadHash, String traceId) {
        ApiResponse<ResourceSlotSnapshot> response;
        try {
            response = resourceClient.getSnapshot(slotId);
        } catch (RuntimeException exception) {
            throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE, exception);
        }
        ResourceSlotSnapshot snapshot = response == null ? null : response.data();
        if (snapshot == null
                || snapshot.outletName() == null
                || snapshot.outletName().isBlank()
                || snapshot.itemName() == null
                || snapshot.itemName().isBlank()) {
            throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
        }
        AppointmentReservationRequestEntity request = new AppointmentReservationRequestEntity();
        request.setReservationId(UUID.randomUUID().toString());
        request.setUserId(userId);
        request.setIdempotencyKeyHash(keyHash);
        request.setPayloadHash(payloadHash);
        request.setSlotId(Long.parseLong(snapshot.slotId()));
        request.setOutletId(Long.parseLong(snapshot.outletId()));
        request.setItemId(Long.parseLong(snapshot.itemId()));
        request.setServiceDate(snapshot.serviceDate());
        request.setSlotStartTime(snapshot.startTime());
        request.setSlotEndTime(snapshot.endTime());
        request.setOutletNameSnapshot(snapshot.outletName());
        request.setItemNameSnapshot(snapshot.itemName());
        request.setTotalQuota(snapshot.totalQuota());
        request.setReleaseAt(snapshot.releaseAt());
        request.setCloseAt(snapshot.closeAt());
        request.setSlotStatus(snapshot.status());
        request.setSlotConfigVersion(snapshot.configVersion());
        request.setStatus(ReservationRequestStatus.CREATED);
        request.setTraceId(traceId);
        request.setPublishAttempts(0);
        request.setNextRecoveryAt(clock.instant());
        try {
            requestMapper.insert(request);
            return request;
        } catch (DuplicateKeyException exception) {
            AppointmentReservationRequestEntity existing = findByIdempotency(userId, keyHash);
            if (existing == null) {
                throw exception;
            }
            if (!Digests.equal(existing.getPayloadHash(), payloadHash)) {
                throw new BusinessException(CommonErrorCode.IDEMPOTENCY_CONFLICT);
            }
            return existing;
        }
    }

    private ReservationRequestedEvent event(AppointmentReservationRequestEntity request) {
        return new ReservationRequestedEvent(
                ReservationRequestedEvent.SCHEMA_VERSION,
                request.getReservationId(),
                ReservationRequestedEvent.EVENT_TYPE,
                ReservationRequestedEvent.EVENT_VERSION,
                request.getReservedAt(),
                ReservationRequestedEvent.PRODUCER,
                request.getTraceId(),
                request.getReservationId(),
                request.getTraceId(),
                new ReservationRequestedEvent.Payload(
                        request.getReservationId(),
                        request.getUserId().toString(),
                        request.getSlotId().toString(),
                        request.getOutletId().toString(),
                        request.getItemId().toString(),
                        request.getOutletNameSnapshot(),
                        request.getItemNameSnapshot(),
                        request.getServiceDate(),
                        request.getSlotStartTime(),
                        request.getSlotEndTime(),
                        request.getTotalQuota(),
                        request.getReleaseAt(),
                        request.getCloseAt(),
                        request.getSlotStatus(),
                        request.getSlotConfigVersion(),
                        request.getReservedAt(),
                        request.getReservationExpiresAt()));
    }

    private ReservationRequestedEvent readEvent(AppointmentReservationRequestEntity request) {
        if (request.getEventJson() == null) {
            return event(request);
        }
        try {
            return objectMapper.readValue(request.getEventJson(), ReservationRequestedEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored reservation event is invalid", exception);
        }
    }

    private String writeEvent(ReservationRequestedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Reservation event cannot be serialized", exception);
        }
    }

    private AppointmentReservationRequestEntity findByIdempotency(long userId, byte[] keyHash) {
        return requestMapper.selectOne(
                Wrappers.<AppointmentReservationRequestEntity>lambdaQuery()
                        .eq(AppointmentReservationRequestEntity::getUserId, userId)
                        .eq(AppointmentReservationRequestEntity::getIdempotencyKeyHash, keyHash));
    }

    private CreateOutcome creating(
            AppointmentReservationRequestEntity request, boolean dependencyUnavailable) {
        String failureCode =
                request.getStatus() == ReservationRequestStatus.PUBLISH_UNKNOWN
                        ? AppointmentErrorCode.PUBLISH_UNKNOWN.code()
                        : null;
        return new CreateOutcome(
                new ReservationCreateResponse(
                        request.getReservationId(),
                        "CREATING",
                        properties.getReservation().getPollAfterMs(),
                        failureCode),
                dependencyUnavailable);
    }

    private CreateOutcome failed(
            AppointmentReservationRequestEntity request, boolean dependencyUnavailable) {
        return new CreateOutcome(
                new ReservationCreateResponse(
                        request.getReservationId(),
                        "FAILED",
                        properties.getReservation().getPollAfterMs(),
                        request.getFailureCode()),
                dependencyUnavailable);
    }

    private static void validate(long userId, long slotId, String idempotencyKey, String traceId) {
        if (userId <= 0 || slotId <= 0 || traceId == null || traceId.isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION);
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(CommonErrorCode.IDEMPOTENCY_REQUIRED);
        }
        if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessException(CommonErrorCode.VALIDATION);
        }
    }
}
