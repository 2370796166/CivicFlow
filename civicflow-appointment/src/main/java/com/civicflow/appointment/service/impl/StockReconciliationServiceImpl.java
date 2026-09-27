package com.civicflow.appointment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.civicflow.appointment.client.ResourceSlotCandidatePage;
import com.civicflow.appointment.client.ResourceSlotClient;
import com.civicflow.appointment.client.ResourceSlotSnapshot;
import com.civicflow.appointment.config.AppointmentProperties;
import com.civicflow.appointment.entity.StockAdminAuditEntity;
import com.civicflow.appointment.entity.StockReconciliationDetailEntity;
import com.civicflow.appointment.entity.StockReconciliationRunEntity;
import com.civicflow.appointment.enums.ReconciliationClassification;
import com.civicflow.appointment.enums.ReconciliationRepairStatus;
import com.civicflow.appointment.enums.ReconciliationRunStatus;
import com.civicflow.appointment.enums.ReconciliationSlotStatus;
import com.civicflow.appointment.enums.ReconciliationTriggerType;
import com.civicflow.appointment.mapper.StockAdminAuditMapper;
import com.civicflow.appointment.mapper.StockReconciliationDetailMapper;
import com.civicflow.appointment.mapper.StockReconciliationMapper;
import com.civicflow.appointment.mapper.StockReconciliationRunMapper;
import com.civicflow.appointment.service.StockReconciliationService;
import com.civicflow.appointment.support.Digests;
import com.civicflow.appointment.support.LuaResult;
import com.civicflow.appointment.support.RedisStockRepository;
import com.civicflow.appointment.support.RedisStockRepository.ReconciliationSnapshot;
import com.civicflow.appointment.support.SlotStockSnapshot;
import com.civicflow.common.api.ApiResponse;
import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class StockReconciliationServiceImpl implements StockReconciliationService {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(StockReconciliationServiceImpl.class);
    private static final int ACTIVE_EVIDENCE_LIMIT = 1000;
    private final ResourceSlotClient resource;
    private final StockReconciliationMapper factsMapper;
    private final StockReconciliationRunMapper runs;
    private final StockReconciliationDetailMapper details;
    private final StockAdminAuditMapper audits;
    private final RedisStockRepository redis;
    private final AppointmentProperties properties;
    private final ObjectMapper json;
    private final Clock clock;
    private final AtomicLong scheduledCursor = new AtomicLong();

    public StockReconciliationServiceImpl(
            ResourceSlotClient resource,
            StockReconciliationMapper factsMapper,
            StockReconciliationRunMapper runs,
            StockReconciliationDetailMapper details,
            StockAdminAuditMapper audits,
            RedisStockRepository redis,
            AppointmentProperties properties,
            ObjectMapper json,
            Clock clock) {
        this.resource = resource;
        this.factsMapper = factsMapper;
        this.runs = runs;
        this.details = details;
        this.audits = audits;
        this.redis = redis;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public Report reconcileOne(
            long slotId, long actorId, String requestId, boolean repair, String idempotencyKey) {
        if (slotId <= 0
                || actorId <= 0
                || requestId == null
                || requestId.isBlank()
                || idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > 128) {
            throw new BusinessException(CommonErrorCode.VALIDATION);
        }
        return reconcile(
                slotId,
                actorId,
                requestId,
                repair,
                ReconciliationTriggerType.ADMIN,
                Digests.sha256(idempotencyKey),
                Digests.sha256(slotId + ":" + repair));
    }

    @Override
    public BatchReport reconcileWindow() {
        Instant scanAt = clock.instant();
        long cursor = scheduledCursor.get();
        int visited = 0;
        int consistent = 0;
        int observed = 0;
        int repaired = 0;
        int alerted = 0;
        int failed = 0;
        int max = properties.getReconciliation().getMaxSlotsPerRun();
        while (visited < max) {
            int size = Math.min(properties.getReconciliation().getPageSize(), max - visited);
            ResourceSlotCandidatePage page =
                    data(resource.getReconciliationCandidates(scanAt, cursor, size));
            if (page.items() == null || page.items().isEmpty()) {
                scheduledCursor.set(0);
                break;
            }
            for (ResourceSlotSnapshot slot : page.items()) {
                visited++;
                try {
                    Report report =
                            reconcile(
                                    Long.parseLong(slot.slotId()),
                                    0,
                                    UUID.randomUUID().toString(),
                                    true,
                                    ReconciliationTriggerType.SCHEDULED,
                                    null,
                                    null);
                    if (report.repairStatus() == ReconciliationRepairStatus.REPAIRED) {
                        repaired++;
                    } else if (report.classification() == ReconciliationClassification.CONSISTENT) {
                        consistent++;
                    } else if (report.classification()
                            == ReconciliationClassification.TRANSIENT_PENDING) {
                        observed++;
                    } else {
                        alerted++;
                    }
                } catch (RuntimeException exception) {
                    failed++;
                    LOGGER.warn(
                            "Stock reconciliation failed slotId={} type={}",
                            slot.slotId(),
                            exception.getClass().getSimpleName());
                }
                if (visited < max && !properties.getReconciliation().getSlotDelay().isZero()) {
                    try {
                        Thread.sleep(properties.getReconciliation().getSlotDelay().toMillis());
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        scheduledCursor.set(Long.parseLong(slot.slotId()));
                        return new BatchReport(
                                visited, consistent, observed, repaired, alerted, failed);
                    }
                }
            }
            if (!page.hasMore()) {
                scheduledCursor.set(0);
                break;
            }
            cursor = Long.parseLong(page.nextCursor());
            scheduledCursor.set(cursor);
        }
        return new BatchReport(visited, consistent, observed, repaired, alerted, failed);
    }

    private Report reconcile(
            long slotId,
            long actorId,
            String requestId,
            boolean repair,
            ReconciliationTriggerType trigger,
            byte[] keyHash,
            byte[] requestHash) {
        Instant started = clock.instant();
        StockReconciliationRunEntity run = new StockReconciliationRunEntity();
        run.setTriggerType(trigger);
        run.setSlotScopeJson(write(Map.of("slotId", Long.toString(slotId))));
        run.setStatus(ReconciliationRunStatus.RUNNING);
        run.setStartedAt(started);
        run.setActorId(actorId);
        run.setIdempotencyKeyHash(keyHash);
        run.setRequestHash(requestHash);
        try {
            runs.insert(run);
        } catch (DuplicateKeyException exception) {
            StockReconciliationRunEntity prior =
                    runs.selectOne(
                            Wrappers.<StockReconciliationRunEntity>lambdaQuery()
                                    .eq(StockReconciliationRunEntity::getActorId, actorId)
                                    .eq(
                                            StockReconciliationRunEntity::getIdempotencyKeyHash,
                                            keyHash));
            if (prior == null || !Digests.equal(prior.getRequestHash(), requestHash)) {
                throw new BusinessException(CommonErrorCode.IDEMPOTENCY_CONFLICT);
            }
            if (prior.getReportJson() == null) {
                throw new BusinessException(
                        com.civicflow.appointment.error.AppointmentErrorCode.STATE_CONFLICT);
            }
            try {
                var stored = json.readTree(prior.getReportJson());
                return json.readValue(
                        stored.isTextual() ? stored.textValue() : prior.getReportJson(),
                        Report.class);
            } catch (JsonProcessingException parseException) {
                throw new IllegalStateException(
                        "Stored reconciliation report is invalid", parseException);
            }
        }
        try {
            Report report = inspect(run, slotId, actorId, requestId, repair);
            run.setStatus(ReconciliationRunStatus.COMPLETED);
            run.setCompletedAt(clock.instant());
            run.setReportJson(write(report));
            runs.updateById(run);
            return report;
        } catch (RuntimeException exception) {
            run.setStatus(ReconciliationRunStatus.FAILED);
            run.setCompletedAt(clock.instant());
            runs.updateById(run);
            throw exception;
        }
    }

    private Report inspect(
            StockReconciliationRunEntity run,
            long slotId,
            long actorId,
            String requestId,
            boolean repair) {
        // A failed CAS is re-read once and reported without attempting a second write.
        StockReconciliationDetailEntity detail = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            Instant detected = clock.instant();
            ResourceSlotSnapshot remote = data(resource.getSnapshot(slotId));
            SlotStockSnapshot slot = remote.toStockSnapshot();
            if (slot.slotId() != slotId) {
                throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
            }
            if (!slot.releaseAt().isAfter(detected) && slot.closeAt().isAfter(detected)) {
                if (!ReconciliationSlotStatus.eligible(slot.status())) {
                    throw new BusinessException(CommonErrorCode.VALIDATION);
                }
                StockReconciliationMapper.Facts facts =
                        factsMapper.selectFacts(
                                slotId,
                                detected.minus(properties.getReconciliation().getGracePeriod()));
                ReconciliationSnapshot stock = redis.reconciliationSnapshot(slot);
                long expected =
                        (long) slot.totalQuota()
                                - facts.getCharged()
                                + facts.getSuccessfulRelease();
                Long actual = stock.remaining() < 0 ? null : stock.remaining();
                Long diff = actual == null ? null : actual - expected;
                boolean missingActive = false;
                var active =
                        factsMapper.selectActiveReservations(slotId, ACTIVE_EVIDENCE_LIMIT + 1);
                boolean incompleteActiveScan = active.size() > ACTIVE_EVIDENCE_LIMIT;
                if (!incompleteActiveScan) {
                    for (var order : active) {
                        String keyValue = redis.activeReservation(slot, order.getUserId());
                        if (!order.getReservationId().equals(keyValue)) {
                            missingActive = true;
                            break;
                        }
                    }
                }
                boolean accounting =
                        facts.getCharged() - facts.getSuccessfulRelease()
                                == facts.getPersistedConsumed()
                                        + facts.getPendingReserved()
                                        + facts.getPendingRelease();
                boolean complete =
                        accounting
                                && facts.getOrphanOrders() == 0
                                && facts.getInvalidRelease() == 0
                                && facts.getInvalidGuard() == 0
                                && !incompleteActiveScan
                                && expected >= 0
                                && expected <= slot.totalQuota();
                ReconciliationClassification classification;
                if (stock.total() < 0 || stock.mutationSeq() < 0 || !complete) {
                    classification = ReconciliationClassification.EVIDENCE_MISSING;
                } else if (stock.configVersion() != slot.configVersion()
                        || stock.total() != slot.totalQuota()) {
                    classification = ReconciliationClassification.CONFIG_VERSION_GAP;
                } else if (facts.getRecentActivity() > 0) {
                    classification = ReconciliationClassification.TRANSIENT_PENDING;
                } else if (missingActive) {
                    classification = ReconciliationClassification.DB_ORDER_REDIS_ACTIVE_MISSING;
                } else if (facts.getPendingCompensations() > 0) {
                    classification = ReconciliationClassification.COMPENSATION_PENDING;
                } else if (facts.getPendingReserved() > 0 || stock.pendingCount() > 0) {
                    classification = ReconciliationClassification.UNRESOLVED_RESERVATION;
                } else if (diff != null && diff == 0) {
                    classification = ReconciliationClassification.CONSISTENT;
                } else {
                    classification = ReconciliationClassification.REDIS_STOCK_MISMATCH;
                }
                boolean safe =
                        classification == ReconciliationClassification.REDIS_STOCK_MISMATCH
                                && diff != null
                                && diff > 0
                                && facts.getPendingReserved() == 0
                                && facts.getPendingRelease() == 0
                                && facts.getPendingCompensations() == 0
                                && stock.pendingCount() == 0
                                && facts.getRecentActivity() == 0;
                if (detail == null) {
                    detail = new StockReconciliationDetailEntity();
                }
                detail.setRunId(run.getId());
                detail.setSlotId(slotId);
                detail.setConfigVersion(slot.configVersion());
                detail.setConfiguredTotal(slot.totalQuota());
                detail.setPersistedConsumed(Math.toIntExact(facts.getPersistedConsumed()));
                detail.setPendingReserved(Math.toIntExact(facts.getPendingReserved()));
                detail.setExpectedRemaining(Math.toIntExact(Math.max(0, expected)));
                detail.setActualRemaining(actual == null ? null : Math.toIntExact(actual));
                detail.setDiff(diff == null ? null : Math.toIntExact(diff));
                detail.setClassification(classification);
                detail.setRepairStatus(
                        classification == ReconciliationClassification.CONSISTENT
                                ? ReconciliationRepairStatus.NOT_REQUIRED
                                : classification == ReconciliationClassification.TRANSIENT_PENDING
                                                || classification
                                                        == ReconciliationClassification
                                                                .COMPENSATION_PENDING
                                        ? ReconciliationRepairStatus.PENDING
                                        : safe && repair && attempt == 0
                                                ? ReconciliationRepairStatus.PENDING
                                                : safe && repair
                                                        ? ReconciliationRepairStatus.FAILED
                                                        : ReconciliationRepairStatus
                                                                .MANUAL_REQUIRED);
                detail.setBeforeJson(
                        write(
                                Map.of(
                                        "mutationSeq",
                                        stock.mutationSeq(),
                                        "charged",
                                        facts.getCharged(),
                                        "successfulRelease",
                                        facts.getSuccessfulRelease(),
                                        "pendingRelease",
                                        facts.getPendingRelease(),
                                        "redisPending",
                                        stock.pendingCount())));
                if (detail.getId() == null) {
                    details.insert(detail);
                } else {
                    details.updateById(detail);
                }
                if (safe && repair && attempt == 0) {
                    // Check resource again immediately before the Redis CAS. Redis mutationSeq
                    // catches reserve/compensate/adjust, including an ABA of remaining.
                    ResourceSlotSnapshot latest = data(resource.getSnapshot(slotId));
                    if (latest.configVersion() != slot.configVersion()
                            || latest.totalQuota() != slot.totalQuota()
                            || !sameFacts(
                                    facts,
                                    factsMapper.selectFacts(
                                            slotId,
                                            clock.instant()
                                                    .minus(
                                                            properties
                                                                    .getReconciliation()
                                                                    .getGracePeriod())))) {
                        continue;
                    }
                    StockAdminAuditEntity audit = new StockAdminAuditEntity();
                    audit.setActorUserId(actorId);
                    audit.setSlotId(slotId);
                    audit.setAction("RECONCILIATION_REPAIR");
                    audit.setOutcome("ATTEMPTED");
                    audit.setRequestId(requestId);
                    audit.setOccurredAt(clock.instant());
                    audit.setDetailJson(
                            write(
                                    Map.of(
                                            "before",
                                            actual,
                                            "expected",
                                            expected,
                                            "configVersion",
                                            slot.configVersion(),
                                            "mutationSeq",
                                            stock.mutationSeq(),
                                            "runId",
                                            run.getId().toString())));
                    audits.insert(audit);
                    LuaResult result =
                            redis.reconciliationRepair(
                                    slot, stock, Math.toIntExact(expected), clock.instant());
                    if (result.code() == 0) {
                        detail.setRepairStatus(ReconciliationRepairStatus.REPAIRED);
                        detail.setAfterJson(
                                write(
                                        Map.of(
                                                "remaining",
                                                result.remaining(),
                                                "mutationSeq",
                                                stock.mutationSeq() + 1)));
                        details.updateById(detail);
                        audit.setOutcome("APPLIED");
                        audit.setDetailJson(
                                write(
                                        Map.of(
                                                "before",
                                                actual,
                                                "after",
                                                result.remaining(),
                                                "expected",
                                                expected,
                                                "configVersion",
                                                slot.configVersion(),
                                                "runId",
                                                run.getId().toString())));
                        audits.updateById(audit);
                    } else {
                        audit.setOutcome("STALE_RECOMPUTE");
                        audits.updateById(audit);
                        continue;
                    }
                }
                if (classification != ReconciliationClassification.CONSISTENT
                        && classification != ReconciliationClassification.TRANSIENT_PENDING
                        && detail.getRepairStatus() != ReconciliationRepairStatus.REPAIRED) {
                    LOGGER.warn(
                            "Stock reconciliation alert slotId={} classification={} expected={} actual={}",
                            slotId,
                            classification,
                            expected,
                            actual);
                }
                return new Report(
                        run.getId().toString(),
                        Long.toString(slotId),
                        expected,
                        actual,
                        diff,
                        slot.totalQuota(),
                        facts.getPersistedConsumed(),
                        facts.getPendingReserved(),
                        facts.getSuccessfulRelease(),
                        facts.getPendingRelease(),
                        classification,
                        detected,
                        safe,
                        detail.getRepairStatus());
            }
            throw new BusinessException(CommonErrorCode.VALIDATION);
        }
        throw new IllegalStateException("Reconciliation could not stabilize");
    }

    private static <T> T data(ApiResponse<T> response) {
        if (response == null
                || !CommonErrorCode.SUCCESS.code().equals(response.code())
                || response.data() == null) {
            throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
        }
        return response.data();
    }

    private static boolean sameFacts(
            StockReconciliationMapper.Facts first, StockReconciliationMapper.Facts second) {
        return first.getCharged() == second.getCharged()
                && first.getSuccessfulRelease() == second.getSuccessfulRelease()
                && first.getPersistedConsumed() == second.getPersistedConsumed()
                && first.getPendingReserved() == second.getPendingReserved()
                && first.getPendingRelease() == second.getPendingRelease()
                && first.getPendingCompensations() == second.getPendingCompensations()
                && first.getOrphanOrders() == second.getOrphanOrders()
                && first.getInvalidRelease() == second.getInvalidRelease()
                && first.getInvalidGuard() == second.getInvalidGuard()
                && second.getRecentActivity() == 0;
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize reconciliation evidence", exception);
        }
    }
}
