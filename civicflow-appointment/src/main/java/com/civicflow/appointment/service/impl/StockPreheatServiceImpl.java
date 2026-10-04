package com.civicflow.appointment.service.impl;

import com.civicflow.appointment.client.ResourceSlotCandidatePage;
import com.civicflow.appointment.client.ResourceSlotClient;
import com.civicflow.appointment.client.ResourceSlotSnapshot;
import com.civicflow.appointment.config.AppointmentProperties;
import com.civicflow.appointment.entity.StockAdminAuditEntity;
import com.civicflow.appointment.error.AppointmentErrorCode;
import com.civicflow.appointment.mapper.StockAdminAuditMapper;
import com.civicflow.appointment.service.StockPreheatService;
import com.civicflow.appointment.support.LuaResult;
import com.civicflow.appointment.support.PreheatScriptResultCode;
import com.civicflow.appointment.support.RedisStockRepository;
import com.civicflow.appointment.support.SlotStockSnapshot;
import com.civicflow.common.api.ApiResponse;
import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StockPreheatServiceImpl implements StockPreheatService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StockPreheatServiceImpl.class);

    private final ResourceSlotClient resourceClient;
    private final RedisStockRepository repository;
    private final AppointmentProperties properties;
    private final Clock clock;
    private final StockAdminAuditMapper auditMapper;
    private final ObjectMapper objectMapper;

    public StockPreheatServiceImpl(
            ResourceSlotClient resourceClient,
            RedisStockRepository repository,
            AppointmentProperties properties,
            Clock clock,
            StockAdminAuditMapper auditMapper,
            ObjectMapper objectMapper) {
        this.resourceClient = resourceClient;
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
        this.auditMapper = auditMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public PreheatResult preheat(ResourceSlotSnapshot remoteSnapshot) {
        SlotStockSnapshot snapshot = remoteSnapshot.toStockSnapshot();
        Instant now = clock.instant();
        Instant expiresAt = snapshot.closeAt().plus(properties.getStock().getRetentionAfterClose());
        LuaResult initial = repository.initialize(snapshot, now, expiresAt);
        PreheatScriptResultCode initialCode = PreheatScriptResultCode.from(initial.code());
        if (initialCode == PreheatScriptResultCode.REQUIRES_ADJUST) {
            return adjusted(remoteSnapshot.slotId(), repository.adjust(snapshot, now, expiresAt));
        }
        return resolved(remoteSnapshot.slotId(), initialCode, initial, "INITIALIZED");
    }

    @Override
    public PreheatResult preheatOne(long slotId, long actorId, String requestId) {
        PreheatResult result = preheat(requireData(resourceClient.getSnapshot(slotId)));
        StockAdminAuditEntity audit = new StockAdminAuditEntity();
        audit.setActorUserId(actorId);
        audit.setSlotId(slotId);
        audit.setAction("PREHEAT_SLOT");
        audit.setOutcome(result.outcome());
        audit.setRequestId(requestId);
        audit.setDetailJson(auditDetail(result));
        audit.setOccurredAt(clock.instant());
        auditMapper.insert(audit);
        return result;
    }

    @Override
    public PreheatBatchResult preheatWindow() {
        Instant from = clock.instant();
        Instant to = from.plus(properties.getPreheat().getFutureWindow());
        long cursor = 0;
        int visited = 0;
        int applied = 0;
        int unchanged = 0;
        int failed = 0;
        while (true) {
            ResourceSlotCandidatePage page =
                    requireData(
                            resourceClient.getPreheatCandidates(
                                    from, to, cursor, properties.getPreheat().getPageSize()));
            for (ResourceSlotSnapshot snapshot : page.items()) {
                visited++;
                try {
                    PreheatResult result = preheat(snapshot);
                    if ("INITIALIZED".equals(result.outcome())
                            || "ADJUSTED".equals(result.outcome())) {
                        applied++;
                    } else {
                        unchanged++;
                    }
                } catch (RuntimeException exception) {
                    failed++;
                    LOGGER.warn(
                            "Slot preheat failed slotId={} type={}",
                            snapshot.slotId(),
                            exception.getClass().getSimpleName());
                }
            }
            if (!page.hasMore()) {
                return new PreheatBatchResult(visited, applied, unchanged, failed);
            }
            cursor = parseCursor(page.nextCursor());
        }
    }

    private PreheatResult adjusted(String slotId, LuaResult result) {
        return resolved(slotId, PreheatScriptResultCode.from(result.code()), result, "ADJUSTED");
    }

    private static PreheatResult resolved(
            String slotId, PreheatScriptResultCode code, LuaResult result, String appliedOutcome) {
        return switch (code) {
            case APPLIED ->
                    new PreheatResult(
                            slotId, appliedOutcome, result.remaining(), result.configVersion());
            case IDEMPOTENT ->
                    new PreheatResult(
                            slotId, "UNCHANGED", result.remaining(), result.configVersion());
            case STALE ->
                    new PreheatResult(
                            slotId, "STALE_IGNORED", result.remaining(), result.configVersion());
            case VERSION_GAP -> throw new BusinessException(AppointmentErrorCode.SLOT_VERSION_GAP);
            case UNSAFE_MISSING ->
                    throw new BusinessException(AppointmentErrorCode.STOCK_REBUILD_UNSAFE);
            case SNAPSHOT_CONFLICT, QUOTA_BELOW_CONSUMED ->
                    throw new BusinessException(AppointmentErrorCode.STOCK_INVARIANT_BROKEN);
            case REQUIRES_ADJUST -> throw new IllegalStateException("Adjustment was not executed");
        };
    }

    private static <T> T requireData(ApiResponse<T> response) {
        if (response == null
                || !CommonErrorCode.SUCCESS.code().equals(response.code())
                || response.data() == null) {
            throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
        }
        return response.data();
    }

    private static long parseCursor(String cursor) {
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException exception) {
            throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE, exception);
        }
    }

    private String auditDetail(PreheatResult result) {
        try {
            return objectMapper.writeValueAsString(
                    java.util.Map.of(
                            "slotId", result.slotId(),
                            "outcome", result.outcome(),
                            "remaining", result.remaining(),
                            "configVersion", result.configVersion()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize stock admin audit", exception);
        }
    }
}
