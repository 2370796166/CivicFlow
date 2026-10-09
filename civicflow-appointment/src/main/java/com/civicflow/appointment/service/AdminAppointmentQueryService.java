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
import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.api.PageResponse;
import com.civicflow.common.exception.BusinessException;
import java.time.LocalDate;
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
            Long userId,
            Long outletId,
            LocalDate serviceDate,
            AppointmentStatus status,
            int page,
            int size) {
        validatePage(page, size);
        if ((userId != null && userId <= 0) || (outletId != null && outletId <= 0)) {
            throw new BusinessException(CommonErrorCode.VALIDATION);
        }
        var query =
                Wrappers.<AppointmentOrderEntity>lambdaQuery()
                        .eq(userId != null, AppointmentOrderEntity::getUserId, userId)
                        .eq(outletId != null, AppointmentOrderEntity::getOutletId, outletId)
                        .eq(
                                serviceDate != null,
                                AppointmentOrderEntity::getServiceDate,
                                serviceDate)
                        .eq(status != null, AppointmentOrderEntity::getStatus, status);
        long total = orders.selectCount(query);
        query.orderByDesc(AppointmentOrderEntity::getCreatedAt, AppointmentOrderEntity::getId)
                .last(pageLimit(page, size));
        return PageResponse.of(
                orders.selectList(query).stream().map(AdminAppointmentQueryService::view).toList(),
                page,
                size,
                total);
    }

    public AdminAppointmentResponse get(long id) {
        if (id <= 0) throw new BusinessException(CommonErrorCode.VALIDATION);
        AppointmentOrderEntity order = orders.selectById(id);
        if (order == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return view(order);
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

    private static AdminAppointmentResponse view(AppointmentOrderEntity order) {
        return new AdminAppointmentResponse(
                order.getUserId().toString(), AppointmentConverter.toResponse(order));
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
