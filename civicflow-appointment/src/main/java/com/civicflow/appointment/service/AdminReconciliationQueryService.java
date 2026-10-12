package com.civicflow.appointment.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.civicflow.appointment.entity.StockReconciliationRunEntity;
import com.civicflow.appointment.enums.ReconciliationRunStatus;
import com.civicflow.appointment.mapper.StockReconciliationRunMapper;
import com.civicflow.common.api.AdminQueryWindow;
import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.api.OperationLogResponse;
import com.civicflow.common.api.PageResponse;
import com.civicflow.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminReconciliationQueryService {
    private final StockReconciliationRunMapper runs;
    private final ObjectMapper json;

    public AdminReconciliationQueryService(StockReconciliationRunMapper runs, ObjectMapper json) {
        this.runs = runs;
        this.json = json;
    }

    public PageResponse<History> list(
            Long slotId, ReconciliationRunStatus status, AdminQueryWindow window) {
        var query =
                Wrappers.<StockReconciliationRunEntity>lambdaQuery()
                        .eq(slotId != null, StockReconciliationRunEntity::getSlotId, slotId)
                        .eq(status != null, StockReconciliationRunEntity::getStatus, status)
                        .ge(
                                window.dateFrom() != null,
                                StockReconciliationRunEntity::getStartedAt,
                                window.fromInstant())
                        .lt(
                                window.dateTo() != null,
                                StockReconciliationRunEntity::getStartedAt,
                                window.toExclusiveInstant());
        long total = runs.selectCount(query);
        query.select(
                        StockReconciliationRunEntity::getId,
                        StockReconciliationRunEntity::getSlotId,
                        StockReconciliationRunEntity::getTriggerType,
                        StockReconciliationRunEntity::getStatus,
                        StockReconciliationRunEntity::getStartedAt,
                        StockReconciliationRunEntity::getCompletedAt,
                        StockReconciliationRunEntity::getActorId,
                        StockReconciliationRunEntity::getFailureCode)
                .orderByDesc(
                        StockReconciliationRunEntity::getStartedAt,
                        StockReconciliationRunEntity::getId)
                .last("LIMIT " + window.size() + " OFFSET " + window.offset());
        return PageResponse.of(
                runs.selectList(query).stream().map(row -> history(row, null)).toList(),
                window.page(),
                window.size(),
                total);
    }

    public History get(long id) {
        var row = runs.selectById(id);
        if (row == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        StockReconciliationService.Report report = null;
        if (row.getReportJson() != null) {
            try {
                var stored = json.readTree(row.getReportJson());
                report =
                        json.readValue(
                                stored.isTextual() ? stored.textValue() : row.getReportJson(),
                                StockReconciliationService.Report.class);
            } catch (JsonProcessingException exception) {
                throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE, exception);
            }
        }
        return history(row, report);
    }

    private static History history(
            StockReconciliationRunEntity row, StockReconciliationService.Report report) {
        return new History(
                row.getId().toString(),
                OperationLogResponse.text(row.getSlotId()),
                row.getTriggerType().name(),
                row.getStatus().name(),
                row.getStartedAt(),
                row.getCompletedAt(),
                OperationLogResponse.text(row.getActorId()),
                row.getFailureCode(),
                report);
    }

    public record History(
            String runId,
            String slotId,
            String triggerType,
            String status,
            Instant startedAt,
            Instant completedAt,
            String actorId,
            String failureCode,
            StockReconciliationService.Report report) {}
}
