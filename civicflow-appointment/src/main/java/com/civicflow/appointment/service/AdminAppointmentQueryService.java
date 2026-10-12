package com.civicflow.appointment.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.civicflow.appointment.convert.AppointmentConverter;
import com.civicflow.appointment.dto.response.AdminAppointmentResponse;
import com.civicflow.appointment.dto.response.AppointmentLogResponse;
import com.civicflow.appointment.entity.AppointmentOperationLogEntity;
import com.civicflow.appointment.entity.AppointmentOrderEntity;
import com.civicflow.appointment.enums.AppointmentOperation;
import com.civicflow.appointment.enums.AppointmentStatus;
import com.civicflow.appointment.mapper.AppointmentOperationLogMapper;
import com.civicflow.appointment.mapper.AppointmentOrderMapper;
import com.civicflow.common.api.AdminQueryWindow;
import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.api.OperationLogResponse;
import com.civicflow.common.api.PageResponse;
import com.civicflow.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminAppointmentQueryService {
    private final AppointmentOrderMapper orders;
    private final AppointmentOperationLogMapper logs;

    public AdminAppointmentQueryService(
            AppointmentOrderMapper orders, AppointmentOperationLogMapper logs) {
        this.orders = orders;
        this.logs = logs;
    }

    public PageResponse<AdminAppointmentResponse> list(
            String keyword,
            Long userId,
            Long outletId,
            Long itemId,
            Long slotId,
            AppointmentStatus status,
            AdminQueryWindow window) {
        var query =
                Wrappers.<AppointmentOrderEntity>lambdaQuery()
                        .eq(userId != null, AppointmentOrderEntity::getUserId, userId)
                        .eq(outletId != null, AppointmentOrderEntity::getOutletId, outletId)
                        .eq(itemId != null, AppointmentOrderEntity::getItemId, itemId)
                        .eq(slotId != null, AppointmentOrderEntity::getSlotId, slotId)
                        .eq(status != null, AppointmentOrderEntity::getStatus, status)
                        .ge(
                                window.dateFrom() != null,
                                AppointmentOrderEntity::getServiceDate,
                                window.dateFrom())
                        .le(
                                window.dateTo() != null,
                                AppointmentOrderEntity::getServiceDate,
                                window.dateTo());
        if (keyword != null && !keyword.isBlank()) {
            String term = keyword.trim();
            Long numericId;
            try {
                numericId = Long.valueOf(term);
            } catch (NumberFormatException ignored) {
                numericId = null;
            }
            Long id = numericId;
            query.and(
                    q -> {
                        q.like(AppointmentOrderEntity::getReservationId, term)
                                .or()
                                .like(AppointmentOrderEntity::getOutletNameSnapshot, term)
                                .or()
                                .like(AppointmentOrderEntity::getItemNameSnapshot, term);
                        if (id != null) q.or().eq(AppointmentOrderEntity::getId, id);
                    });
        }
        long total = orders.selectCount(query);
        query.orderByDesc(
                        AppointmentOrderEntity::getServiceDate,
                        AppointmentOrderEntity::getSlotStartTime,
                        AppointmentOrderEntity::getId)
                .last("LIMIT " + window.size() + " OFFSET " + window.offset());
        return PageResponse.of(
                orders.selectList(query).stream()
                        .map(AdminAppointmentQueryService::response)
                        .toList(),
                window.page(),
                window.size(),
                total);
    }

    public AdminAppointmentResponse get(long id) {
        var row = orders.selectById(id);
        if (row == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return response(row);
    }

    private static AdminAppointmentResponse response(AppointmentOrderEntity row) {
        return new AdminAppointmentResponse(
                OperationLogResponse.text(row.getUserId()),
                OperationLogResponse.text(row.getQueueTicketId()),
                AppointmentConverter.toResponse(row),
                row.getConfirmedAt(),
                row.getCancelledAt(),
                row.getExpiredAt(),
                row.getCheckedInAt(),
                row.getServingAt(),
                row.getCompletedAt(),
                row.getNoShowAt());
    }

    public PageResponse<AppointmentLogResponse> logs(
            Long appointmentId, AppointmentOperation operation, int page, int size) {
        validatePage(page, size);
        if (appointmentId != null) get(appointmentId);
        var query =
                Wrappers.<AppointmentOperationLogEntity>lambdaQuery()
                        .eq(
                                appointmentId != null,
                                AppointmentOperationLogEntity::getAppointmentId,
                                appointmentId)
                        .eq(
                                operation != null,
                                AppointmentOperationLogEntity::getOperation,
                                operation);
        long total = logs.selectCount(query);
        query.orderByDesc(
                        AppointmentOperationLogEntity::getOccurredAt,
                        AppointmentOperationLogEntity::getId)
                .last(pageLimit(page, size));
        return PageResponse.of(
                logs.selectList(query).stream()
                        .map(
                                log ->
                                        new AppointmentLogResponse(
                                                log.getId().toString(),
                                                log.getAppointmentId().toString(),
                                                log.getReservationId(),
                                                log.getActorType().name(),
                                                log.getActorId() == null
                                                        ? null
                                                        : log.getActorId().toString(),
                                                log.getOperation().name(),
                                                log.getFromStatus() == null
                                                        ? null
                                                        : log.getFromStatus().name(),
                                                log.getToStatus() == null
                                                        ? null
                                                        : log.getToStatus().name(),
                                                log.getRequestId(),
                                                log.getOccurredAt()))
                        .toList(),
                page,
                size,
                total);
    }

    private static void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100)
            throw new BusinessException(CommonErrorCode.VALIDATION);
    }

    private static String pageLimit(int page, int size) {
        // Only validated integers enter this SQL fragment; all filters use bound parameters.
        return "LIMIT " + size + " OFFSET " + ((long) (page - 1) * size);
    }
}
