package com.civicflow.resource.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.api.GlobalErrorCode;
import com.civicflow.common.api.PageResponse;
import com.civicflow.common.exception.BusinessException;
import com.civicflow.resource.config.ContactPhoneProtector;
import com.civicflow.resource.convert.ResourceConverter;
import com.civicflow.resource.dto.request.AdjustSlotQuotaRequest;
import com.civicflow.resource.dto.request.BatchCreateSlotsRequest;
import com.civicflow.resource.dto.request.ChangeSlotStatusRequest;
import com.civicflow.resource.dto.request.CreateSlotRequest;
import com.civicflow.resource.dto.request.UpdateSlotRequest;
import com.civicflow.resource.dto.response.SlotBatchFailureResponse;
import com.civicflow.resource.dto.response.SlotBatchResponse;
import com.civicflow.resource.dto.response.SlotResponse;
import com.civicflow.resource.entity.ResourceAdminAuditEntity;
import com.civicflow.resource.entity.ResourceAdminIdempotencyEntity;
import com.civicflow.resource.entity.ResourceSlotBatchResultEntity;
import com.civicflow.resource.entity.ResourceSlotDayLockEntity;
import com.civicflow.resource.entity.ResourceSlotEntity;
import com.civicflow.resource.entity.ResourceSlotOutboxEntity;
import com.civicflow.resource.entity.ServiceItemEntity;
import com.civicflow.resource.entity.ServiceOutletEntity;
import com.civicflow.resource.enums.ResourceAdminOperation;
import com.civicflow.resource.enums.ResourceStatus;
import com.civicflow.resource.enums.SlotStatus;
import com.civicflow.resource.error.ResourceErrorCode;
import com.civicflow.resource.mapper.ResourceAdminAuditMapper;
import com.civicflow.resource.mapper.ResourceAdminIdempotencyMapper;
import com.civicflow.resource.mapper.ResourceSlotBatchResultMapper;
import com.civicflow.resource.mapper.ResourceSlotDayLockMapper;
import com.civicflow.resource.mapper.ResourceSlotMapper;
import com.civicflow.resource.mapper.ResourceSlotOutboxMapper;
import com.civicflow.resource.mapper.ServiceItemMapper;
import com.civicflow.resource.mapper.ServiceOutletMapper;
import com.civicflow.resource.service.SlotAdminService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SlotAdminServiceImpl implements SlotAdminService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_BATCH_DAYS = 31;
    private static final int MAX_CONFIG_DAYS_AHEAD = 365;
    private static final String EVENT_TYPE = "resource.slot.changed";
    private static final String ROUTING_KEY = "resource.slot.changed.v1";

    private final ResourceSlotMapper slotMapper;
    private final ResourceSlotDayLockMapper dayLockMapper;
    private final ResourceSlotBatchResultMapper batchResultMapper;
    private final ResourceSlotOutboxMapper outboxMapper;
    private final ServiceOutletMapper outletMapper;
    private final ServiceItemMapper itemMapper;
    private final ResourceAdminIdempotencyMapper idempotencyMapper;
    private final ResourceAdminAuditMapper auditMapper;
    private final ContactPhoneProtector payloadProtector;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SlotAdminServiceImpl(
            ResourceSlotMapper slotMapper,
            ResourceSlotDayLockMapper dayLockMapper,
            ResourceSlotBatchResultMapper batchResultMapper,
            ResourceSlotOutboxMapper outboxMapper,
            ServiceOutletMapper outletMapper,
            ServiceItemMapper itemMapper,
            ResourceAdminIdempotencyMapper idempotencyMapper,
            ResourceAdminAuditMapper auditMapper,
            ContactPhoneProtector payloadProtector,
            ObjectMapper objectMapper,
            Clock clock) {
        this.slotMapper = slotMapper;
        this.dayLockMapper = dayLockMapper;
        this.batchResultMapper = batchResultMapper;
        this.outboxMapper = outboxMapper;
        this.outletMapper = outletMapper;
        this.itemMapper = itemMapper;
        this.idempotencyMapper = idempotencyMapper;
        this.auditMapper = auditMapper;
        this.payloadProtector = payloadProtector;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SlotResponse> list(
            Long outletId,
            Long itemId,
            LocalDate dateFrom,
            LocalDate dateTo,
            SlotStatus status,
            int page,
            int size) {
        if (dateFrom != null && dateTo != null && dateTo.isBefore(dateFrom)) {
            throw new BusinessException(CommonErrorCode.VALIDATION);
        }
        long total = slotMapper.countAdminPage(outletId, itemId, dateFrom, dateTo, status);
        List<SlotResponse> items =
                slotMapper
                        .selectAdminPage(
                                outletId,
                                itemId,
                                dateFrom,
                                dateTo,
                                status,
                                (long) (page - 1) * size,
                                size)
                        .stream()
                        .map(ResourceConverter::toSlot)
                        .toList();
        return PageResponse.of(items, page, size, total);
    }

    @Override
    @Transactional(readOnly = true)
    public SlotResponse get(long id) {
        return ResourceConverter.toSlot(requireSlot(id));
    }

    @Override
    @Transactional
    public SlotResponse create(
            long actorId, String idempotencyKey, String requestId, CreateSlotRequest request) {
        ResourceAdminIdempotencyEntity operation =
                reserve(
                        actorId,
                        ResourceAdminOperation.CREATE_SLOT,
                        idempotencyKey,
                        canonical(request));
        if (operation.getResourceId() != null) {
            return ResourceConverter.toSlot(requireSlot(operation.getResourceId()));
        }
        long outletId = parseId(request.outletId());
        long itemId = parseId(request.itemId());
        validateSlot(
                request.serviceDate(),
                request.startTime(),
                request.endTime(),
                request.releaseAt(),
                request.checkInStart(),
                request.checkInEnd(),
                request.status());
        lockEnabledContext(outletId, itemId);
        lockDay(outletId, itemId, request.serviceDate());
        requireNoOverlap(
                outletId,
                itemId,
                request.serviceDate(),
                request.startTime(),
                request.endTime(),
                null);
        ResourceSlotEntity entity =
                newSlot(
                        actorId,
                        outletId,
                        itemId,
                        request.serviceDate(),
                        request.startTime(),
                        request.endTime(),
                        request.totalQuota(),
                        request.releaseAt(),
                        request.checkInStart(),
                        request.checkInEnd(),
                        request.status());
        try {
            slotMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResourceErrorCode.SLOT_OVERLAP, exception);
        }
        bind(operation, entity.getId());
        if (entity.getStatus() == SlotStatus.SCHEDULED) {
            appendChangedEvent(null, entity, requestId);
        }
        audit(actorId, entity.getId(), operation.getOperation(), requestId, null, snapshot(entity));
        return ResourceConverter.toSlot(entity);
    }

    @Override
    @Transactional
    public SlotBatchResponse batchCreate(
            long actorId,
            String idempotencyKey,
            String requestId,
            BatchCreateSlotsRequest request) {
        validateBatchRange(request.startDate(), request.endDate());
        ResourceAdminIdempotencyEntity operation =
                reserve(
                        actorId,
                        ResourceAdminOperation.BATCH_CREATE_SLOTS,
                        idempotencyKey,
                        canonical(request));
        if (operation.getResourceId() != null) {
            return readBatchResult(operation.getResourceId());
        }
        long outletId = parseId(request.outletId());
        long itemId = parseId(request.itemId());
        lockEnabledContext(outletId, itemId);
        int created = 0;
        int skipped = 0;
        List<SlotBatchFailureResponse> failed = new ArrayList<>();
        for (LocalDate date = request.startDate();
                !date.isAfter(request.endDate());
                date = date.plusDays(1)) {
            Instant releaseAt =
                    date.minusDays(request.releaseDaysBefore())
                            .atTime(request.releaseTime())
                            .atZone(BUSINESS_ZONE)
                            .toInstant();
            Instant checkInStart =
                    date.atTime(request.checkInStart()).atZone(BUSINESS_ZONE).toInstant();
            Instant checkInEnd =
                    date.atTime(request.checkInEnd()).atZone(BUSINESS_ZONE).toInstant();
            try {
                validateSlot(
                        date,
                        request.startTime(),
                        request.endTime(),
                        releaseAt,
                        checkInStart,
                        checkInEnd,
                        request.status());
                lockDay(outletId, itemId, date);
                if (slotMapper.selectExact(
                                outletId, itemId, date, request.startTime(), request.endTime())
                        != null) {
                    skipped++;
                    continue;
                }
                requireNoOverlap(
                        outletId, itemId, date, request.startTime(), request.endTime(), null);
                ResourceSlotEntity entity =
                        newSlot(
                                actorId,
                                outletId,
                                itemId,
                                date,
                                request.startTime(),
                                request.endTime(),
                                request.totalQuota(),
                                releaseAt,
                                checkInStart,
                                checkInEnd,
                                request.status());
                slotMapper.insert(entity);
                if (entity.getStatus() == SlotStatus.SCHEDULED) {
                    appendChangedEvent(null, entity, requestId);
                }
                created++;
            } catch (BusinessException exception) {
                failed.add(failure(date, exception.errorCode()));
            } catch (DuplicateKeyException exception) {
                skipped++;
            }
        }
        SlotBatchResponse response = new SlotBatchResponse(created, skipped, List.copyOf(failed));
        ResourceSlotBatchResultEntity result = new ResourceSlotBatchResultEntity();
        result.setId(operation.getId());
        result.setResultJson(toJson(response));
        batchResultMapper.insert(result);
        bind(operation, result.getId());
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("startDate", request.startDate());
        after.put("endDate", request.endDate());
        after.put("created", created);
        after.put("skipped", skipped);
        after.put("failed", failed.size());
        audit(actorId, result.getId(), operation.getOperation(), requestId, null, after);
        return response;
    }

    @Override
    @Transactional
    public SlotResponse update(
            long actorId,
            long id,
            String idempotencyKey,
            String requestId,
            UpdateSlotRequest request) {
        ResourceAdminIdempotencyEntity operation =
                reserve(
                        actorId,
                        ResourceAdminOperation.UPDATE_SLOT,
                        idempotencyKey,
                        id + "|" + canonical(request));
        if (operation.getResourceId() != null) {
            return ResourceConverter.toSlot(requireSlot(operation.getResourceId()));
        }
        ResourceSlotEntity current = requireSlotForUpdate(id);
        if (current.getVersion() != request.version()) {
            throw new BusinessException(ResourceErrorCode.VERSION_CONFLICT);
        }
        if (current.getStatus() != SlotStatus.DRAFT) {
            throw new BusinessException(ResourceErrorCode.SLOT_STATE_CONFLICT);
        }
        long outletId = parseId(request.outletId());
        long itemId = parseId(request.itemId());
        validateSlot(
                request.serviceDate(),
                request.startTime(),
                request.endTime(),
                request.releaseAt(),
                request.checkInStart(),
                request.checkInEnd(),
                SlotStatus.DRAFT);
        lockEnabledContext(outletId, itemId);
        lockDays(
                List.of(
                        new SlotDay(
                                current.getOutletId(),
                                current.getItemId(),
                                current.getServiceDate()),
                        new SlotDay(outletId, itemId, request.serviceDate())));
        requireNoOverlap(
                outletId,
                itemId,
                request.serviceDate(),
                request.startTime(),
                request.endTime(),
                id);
        int rows =
                slotMapper.updateDraftDetails(
                        id,
                        outletId,
                        itemId,
                        request.serviceDate(),
                        request.startTime(),
                        request.endTime(),
                        request.totalQuota(),
                        request.releaseAt(),
                        request.checkInStart(),
                        request.checkInEnd(),
                        actorId,
                        request.version());
        requireUpdated(rows);
        bind(operation, id);
        ResourceSlotEntity updated = requireSlot(id);
        audit(
                actorId,
                id,
                operation.getOperation(),
                requestId,
                snapshot(current),
                snapshot(updated));
        return ResourceConverter.toSlot(updated);
    }

    @Override
    @Transactional
    public SlotResponse changeStatus(
            long actorId,
            long id,
            String idempotencyKey,
            String requestId,
            ChangeSlotStatusRequest request) {
        ResourceAdminIdempotencyEntity operation =
                reserve(
                        actorId,
                        ResourceAdminOperation.CHANGE_SLOT_STATUS,
                        idempotencyKey,
                        id + "|" + request.status() + "|" + request.version());
        if (operation.getResourceId() != null) {
            return ResourceConverter.toSlot(requireSlot(operation.getResourceId()));
        }
        ResourceSlotEntity current = requireSlotForUpdate(id);
        if (current.getVersion() != request.version()) {
            throw new BusinessException(ResourceErrorCode.VERSION_CONFLICT);
        }
        if (current.getStatus() == request.status()) {
            bind(operation, id);
            return ResourceConverter.toSlot(current);
        }
        validateTransition(current, request.status());
        requireUpdated(slotMapper.updateStatus(id, request.status(), actorId, request.version()));
        bind(operation, id);
        ResourceSlotEntity updated = requireSlot(id);
        appendChangedEvent(current, updated, requestId);
        audit(
                actorId,
                id,
                operation.getOperation(),
                requestId,
                snapshot(current),
                snapshot(updated));
        return ResourceConverter.toSlot(updated);
    }

    @Override
    @Transactional
    public SlotResponse adjustQuota(
            long actorId,
            long id,
            String idempotencyKey,
            String requestId,
            AdjustSlotQuotaRequest request) {
        ResourceAdminIdempotencyEntity operation =
                reserve(
                        actorId,
                        ResourceAdminOperation.ADJUST_SLOT_QUOTA,
                        idempotencyKey,
                        id + "|" + request.totalQuota() + "|" + request.configVersion());
        if (operation.getResourceId() != null) {
            return ResourceConverter.toSlot(requireSlot(operation.getResourceId()));
        }
        ResourceSlotEntity current = requireSlotForUpdate(id);
        if (current.getConfigVersion() != request.configVersion()) {
            throw new BusinessException(ResourceErrorCode.VERSION_CONFLICT);
        }
        if (current.getStatus() == SlotStatus.CLOSED) {
            throw new BusinessException(ResourceErrorCode.SLOT_STATE_CONFLICT);
        }
        if (current.getConsumedHint() != null && request.totalQuota() < current.getConsumedHint()) {
            throw new BusinessException(ResourceErrorCode.QUOTA_BELOW_CONSUMED);
        }
        boolean released =
                !clock.instant().isBefore(current.getReleaseAt())
                        || current.getStatus() == SlotStatus.OPEN
                        || current.getStatus() == SlotStatus.SUSPENDED;
        if (released
                && request.totalQuota() < current.getTotalQuota()
                && current.getConsumedHint() == null) {
            throw new BusinessException(ResourceErrorCode.CONSUMPTION_UNKNOWN);
        }
        if (request.totalQuota() == current.getTotalQuota()) {
            bind(operation, id);
            return ResourceConverter.toSlot(current);
        }
        requireUpdated(
                slotMapper.updateQuota(id, request.totalQuota(), actorId, request.configVersion()));
        bind(operation, id);
        ResourceSlotEntity updated = requireSlot(id);
        appendChangedEvent(current, updated, requestId);
        audit(
                actorId,
                id,
                operation.getOperation(),
                requestId,
                snapshot(current),
                snapshot(updated));
        return ResourceConverter.toSlot(updated);
    }

    private void validateSlot(
            LocalDate serviceDate,
            LocalTime startTime,
            LocalTime endTime,
            Instant releaseAt,
            Instant checkInStart,
            Instant checkInEnd,
            SlotStatus status) {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        if (serviceDate.isBefore(today)
                || serviceDate.isAfter(today.plusDays(MAX_CONFIG_DAYS_AHEAD))
                || !endTime.isAfter(startTime)
                || (status != SlotStatus.DRAFT && status != SlotStatus.SCHEDULED)) {
            throw new BusinessException(CommonErrorCode.VALIDATION);
        }
        Instant slotStart = serviceDate.atTime(startTime).atZone(BUSINESS_ZONE).toInstant();
        Instant slotEnd = serviceDate.atTime(endTime).atZone(BUSINESS_ZONE).toInstant();
        if (!releaseAt.isBefore(slotStart)
                || !checkInStart.isBefore(checkInEnd)
                || checkInStart.isAfter(slotStart)
                || checkInEnd.isBefore(slotStart)
                || checkInEnd.isAfter(slotEnd)
                || (status == SlotStatus.SCHEDULED && !clock.instant().isBefore(releaseAt))) {
            throw new BusinessException(CommonErrorCode.VALIDATION);
        }
    }

    private void validateBatchRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)
                || ChronoUnit.DAYS.between(startDate, endDate) + 1 > MAX_BATCH_DAYS) {
            throw new BusinessException(CommonErrorCode.VALIDATION);
        }
    }

    private void validateTransition(ResourceSlotEntity current, SlotStatus target) {
        Instant now = clock.instant();
        Instant slotEnd =
                current.getServiceDate()
                        .atTime(current.getEndTime())
                        .atZone(BUSINESS_ZONE)
                        .toInstant();
        boolean allowed =
                switch (current.getStatus()) {
                    case DRAFT ->
                            (target == SlotStatus.SCHEDULED && now.isBefore(current.getReleaseAt()))
                                    || (target == SlotStatus.OPEN
                                            && !now.isBefore(current.getReleaseAt())
                                            && now.isBefore(slotEnd))
                                    || target == SlotStatus.CLOSED;
                    case SCHEDULED ->
                            (target == SlotStatus.OPEN && !now.isBefore(current.getReleaseAt()))
                                    || target == SlotStatus.SUSPENDED
                                    || target == SlotStatus.CLOSED;
                    case OPEN -> target == SlotStatus.SUSPENDED || target == SlotStatus.CLOSED;
                    case SUSPENDED ->
                            (target == SlotStatus.SCHEDULED && now.isBefore(current.getReleaseAt()))
                                    || (target == SlotStatus.OPEN
                                            && !now.isBefore(current.getReleaseAt())
                                            && now.isBefore(slotEnd))
                                    || target == SlotStatus.CLOSED;
                    case CLOSED -> false;
                };
        if (!allowed) {
            throw new BusinessException(ResourceErrorCode.SLOT_STATE_CONFLICT);
        }
    }

    private void lockEnabledContext(long outletId, long itemId) {
        ServiceOutletEntity outlet = outletMapper.selectByIdForUpdate(outletId);
        if (outlet == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        ServiceItemEntity item = itemMapper.selectByIdForUpdate(itemId);
        if (item == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        if (outlet.getStatus() != ResourceStatus.ENABLED
                || item.getStatus() != ResourceStatus.ENABLED
                || slotMapper.countEnabledOffering(outletId, itemId) == 0) {
            throw new BusinessException(ResourceErrorCode.SLOT_STATE_CONFLICT);
        }
    }

    private void lockDay(long outletId, long itemId, LocalDate serviceDate) {
        ResourceSlotDayLockEntity candidate = new ResourceSlotDayLockEntity();
        candidate.setId(IdWorker.getId());
        candidate.setOutletId(outletId);
        candidate.setItemId(itemId);
        candidate.setServiceDate(serviceDate);
        candidate.setCreatedAt(clock.instant());
        candidate.setUpdatedAt(clock.instant());
        dayLockMapper.insertIgnore(candidate);
        if (dayLockMapper.selectForUpdate(outletId, itemId, serviceDate) == null) {
            throw new IllegalStateException("Cannot acquire resource slot day lock");
        }
    }

    private void lockDays(List<SlotDay> days) {
        days.stream()
                .distinct()
                .sorted(
                        Comparator.comparingLong(SlotDay::outletId)
                                .thenComparingLong(SlotDay::itemId)
                                .thenComparing(SlotDay::serviceDate))
                .forEach(day -> lockDay(day.outletId(), day.itemId(), day.serviceDate()));
    }

    private void requireNoOverlap(
            long outletId,
            long itemId,
            LocalDate serviceDate,
            LocalTime startTime,
            LocalTime endTime,
            Long excludedId) {
        if (slotMapper.countOverlapping(
                        outletId, itemId, serviceDate, startTime, endTime, excludedId)
                > 0) {
            throw new BusinessException(ResourceErrorCode.SLOT_OVERLAP);
        }
    }

    private ResourceSlotEntity newSlot(
            long actorId,
            long outletId,
            long itemId,
            LocalDate serviceDate,
            LocalTime startTime,
            LocalTime endTime,
            int totalQuota,
            Instant releaseAt,
            Instant checkInStart,
            Instant checkInEnd,
            SlotStatus status) {
        ResourceSlotEntity entity = new ResourceSlotEntity();
        entity.setOutletId(outletId);
        entity.setItemId(itemId);
        entity.setServiceDate(serviceDate);
        entity.setStartTime(startTime);
        entity.setEndTime(endTime);
        entity.setTotalQuota(totalQuota);
        entity.setReleaseAt(releaseAt);
        entity.setCheckInStart(checkInStart);
        entity.setCheckInEnd(checkInEnd);
        entity.setStatus(status);
        entity.setConfigVersion(1L);
        entity.setVersion(0);
        entity.setCreatedBy(actorId);
        entity.setUpdatedBy(actorId);
        return entity;
    }

    private ResourceAdminIdempotencyEntity reserve(
            long actorId,
            ResourceAdminOperation operation,
            String idempotencyKey,
            String canonicalPayload) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BusinessException(CommonErrorCode.IDEMPOTENCY_REQUIRED);
        }
        byte[] payloadHash = payloadProtector.payloadHash(canonicalPayload);
        ResourceAdminIdempotencyEntity candidate = new ResourceAdminIdempotencyEntity();
        candidate.setId(IdWorker.getId());
        candidate.setActorUserId(actorId);
        candidate.setOperation(operation.name());
        candidate.setIdempotencyKey(idempotencyKey);
        candidate.setPayloadHash(payloadHash);
        candidate.setCreatedAt(clock.instant());
        candidate.setUpdatedAt(clock.instant());
        idempotencyMapper.insertIgnore(candidate);
        ResourceAdminIdempotencyEntity stored =
                idempotencyMapper.selectForUpdate(actorId, operation.name(), idempotencyKey);
        if (stored == null || !MessageDigest.isEqual(stored.getPayloadHash(), payloadHash)) {
            throw new BusinessException(CommonErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return stored;
    }

    private void bind(ResourceAdminIdempotencyEntity operation, long resourceId) {
        idempotencyMapper.bindResource(operation.getId(), resourceId);
    }

    private void appendChangedEvent(
            ResourceSlotEntity before, ResourceSlotEntity after, String requestId) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("slotId", after.getId().toString());
        payload.put("oldTotalQuota", before == null ? null : before.getTotalQuota());
        payload.put("newTotalQuota", after.getTotalQuota());
        payload.put("oldStatus", before == null ? null : before.getStatus().name());
        payload.put("newStatus", after.getStatus().name());
        payload.put("configVersion", after.getConfigVersion());
        payload.put("changedAt", clock.instant());
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", EVENT_TYPE);
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", clock.instant());
        envelope.put("traceId", requestId);
        envelope.put("correlationId", after.getId().toString());
        envelope.put("causationId", requestId);
        envelope.put("producer", "civicflow-resource");
        envelope.put("payload", payload);
        ResourceSlotOutboxEntity outbox = new ResourceSlotOutboxEntity();
        outbox.setEventId(eventId);
        outbox.setSlotId(after.getId());
        outbox.setEventType(EVENT_TYPE);
        outbox.setEventVersion(1);
        outbox.setRoutingKey(ROUTING_KEY);
        outbox.setPayloadJson(toJson(envelope));
        outbox.setStatus("PENDING");
        outbox.setAttempts(0);
        outbox.setNextAttemptAt(clock.instant());
        outboxMapper.insert(outbox);
    }

    private void audit(
            long actorId,
            long resourceId,
            String action,
            String requestId,
            Map<String, Object> before,
            Map<String, Object> after) {
        ResourceAdminAuditEntity audit = new ResourceAdminAuditEntity();
        audit.setActorUserId(actorId);
        audit.setResourceType(
                action.equals(ResourceAdminOperation.BATCH_CREATE_SLOTS.name())
                        ? "SLOT_BATCH"
                        : "SLOT");
        audit.setResourceId(resourceId);
        audit.setAction(action);
        audit.setRequestId(requestId);
        audit.setBeforeJson(before == null ? null : toJson(before));
        audit.setAfterJson(after == null ? null : toJson(after));
        audit.setOccurredAt(clock.instant());
        auditMapper.insert(audit);
    }

    private ResourceSlotEntity requireSlot(long id) {
        ResourceSlotEntity entity = slotMapper.selectActiveById(id);
        if (entity == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return entity;
    }

    private ResourceSlotEntity requireSlotForUpdate(long id) {
        ResourceSlotEntity entity = slotMapper.selectByIdForUpdate(id);
        if (entity == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return entity;
    }

    private SlotBatchResponse readBatchResult(long id) {
        ResourceSlotBatchResultEntity result = batchResultMapper.selectById(id);
        if (result == null) {
            throw new IllegalStateException("Missing idempotent resource slot batch result");
        }
        try {
            String json = unwrapJsonString(result.getResultJson());
            return objectMapper.readValue(json, SlotBatchResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Cannot deserialize resource slot batch result", exception);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize resource slot data", exception);
        }
    }

    private String unwrapJsonString(String value) throws JsonProcessingException {
        if (value != null && value.startsWith("\"") && value.endsWith("\"")) {
            return objectMapper.readValue(value, String.class);
        }
        return value;
    }

    private static void requireUpdated(int rows) {
        if (rows != 1) {
            throw new BusinessException(ResourceErrorCode.VERSION_CONFLICT);
        }
    }

    private static long parseId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(CommonErrorCode.VALIDATION, exception);
        }
    }

    private static String canonical(Object request) {
        return request.toString();
    }

    private static SlotBatchFailureResponse failure(LocalDate date, GlobalErrorCode errorCode) {
        return new SlotBatchFailureResponse(date, errorCode.code(), errorCode.message());
    }

    private static Map<String, Object> snapshot(ResourceSlotEntity entity) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("outletId", entity.getOutletId().toString());
        value.put("itemId", entity.getItemId().toString());
        value.put("serviceDate", entity.getServiceDate());
        value.put("startTime", entity.getStartTime());
        value.put("endTime", entity.getEndTime());
        value.put("totalQuota", entity.getTotalQuota());
        value.put("releaseAt", entity.getReleaseAt());
        value.put("checkInStart", entity.getCheckInStart());
        value.put("checkInEnd", entity.getCheckInEnd());
        value.put("status", entity.getStatus().name());
        value.put("configVersion", entity.getConfigVersion());
        value.put("consumedHint", entity.getConsumedHint());
        value.put("version", entity.getVersion());
        return value;
    }

    private record SlotDay(long outletId, long itemId, LocalDate serviceDate) {}
}
