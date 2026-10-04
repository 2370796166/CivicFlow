package com.civicflow.appointment.service;

import com.civicflow.appointment.enums.ReconciliationClassification;
import com.civicflow.appointment.enums.ReconciliationRepairStatus;
import java.time.Instant;

public interface StockReconciliationService {
    Report reconcileOne(
            long slotId, long actorId, String requestId, boolean repair, String idempotencyKey);

    BatchReport reconcileWindow();

    record Report(
            String runId,
            String slotId,
            long expected,
            Long actual,
            Long diff,
            long configuredTotal,
            long persistedConsumed,
            long pendingReserved,
            long successfulCompensations,
            long pendingCompensations,
            ReconciliationClassification classification,
            Instant detectedAt,
            boolean autoRepairable,
            ReconciliationRepairStatus repairStatus) {}

    record BatchReport(
            int visited, int consistent, int observed, int repaired, int alerted, int failed) {}
}
