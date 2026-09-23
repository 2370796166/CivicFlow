package com.civicflow.appointment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.civicflow.appointment.convert.AppointmentConverter;
import com.civicflow.appointment.dto.response.AppointmentResponse;
import com.civicflow.appointment.dto.response.ReservationStatusResponse;
import com.civicflow.appointment.entity.AppointmentOrderEntity;
import com.civicflow.appointment.entity.AppointmentReservationRequestEntity;
import com.civicflow.appointment.enums.AppointmentStatus;
import com.civicflow.appointment.enums.ReservationRequestStatus;
import com.civicflow.appointment.error.AppointmentErrorCode;
import com.civicflow.appointment.mapper.AppointmentOrderMapper;
import com.civicflow.appointment.mapper.AppointmentReservationRequestMapper;
import com.civicflow.appointment.service.AppointmentQueryService;
import com.civicflow.appointment.service.AppointmentStateService;
import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.api.PageResponse;
import com.civicflow.common.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AppointmentQueryServiceImpl implements AppointmentQueryService {
    private final AppointmentOrderMapper orderMapper;
    private final AppointmentReservationRequestMapper requestMapper;
    private final AppointmentStateService stateService;
    private final Clock clock;

    public AppointmentQueryServiceImpl(
            AppointmentOrderMapper orderMapper,
            AppointmentReservationRequestMapper requestMapper,
            AppointmentStateService stateService,
            Clock clock) {
        this.orderMapper = orderMapper;
        this.requestMapper = requestMapper;
        this.stateService = stateService;
        this.clock = clock;
    }

    @Override
    public ReservationStatusResponse getReservation(long userId, String reservationId) {
        if (userId <= 0 || reservationId == null || reservationId.isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION);
        }
        AppointmentOrderEntity order = orderMapper.selectByReservationId(reservationId);
        if (order != null) {
            if (order.getUserId() != userId) {
                throw new BusinessException(CommonErrorCode.NOT_FOUND);
            }
            order = settleExpired(order);
            return new ReservationStatusResponse(
                    reservationId,
                    order.getStatus().name(),
                    AppointmentConverter.toResponse(order),
                    null);
        }
        AppointmentReservationRequestEntity request =
                requestMapper.selectOne(
                        Wrappers.<AppointmentReservationRequestEntity>lambdaQuery()
                                .eq(
                                        AppointmentReservationRequestEntity::getReservationId,
                                        reservationId)
                                .eq(AppointmentReservationRequestEntity::getUserId, userId));
        if (request == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        boolean failed =
                request.getStatus() == ReservationRequestStatus.FAILED
                        || request.getStatus() == ReservationRequestStatus.COMPENSATION_PENDING;
        String failureCode =
                failed
                        ? request.getFailureCode()
                        : request.getStatus() == ReservationRequestStatus.PUBLISH_UNKNOWN
                                ? AppointmentErrorCode.PUBLISH_UNKNOWN.code()
                                : null;
        return new ReservationStatusResponse(
                reservationId, failed ? "FAILED" : "CREATING", null, failureCode);
    }

    @Override
    public PageResponse<AppointmentResponse> listOwned(
            long userId, LocalDate serviceDate, AppointmentStatus status, int page, int size) {
        validatePage(userId, page, size);
        long offset = (long) (page - 1) * size;
        List<AppointmentResponse> items =
                orderMapper.selectOwnedPage(userId, serviceDate, status, offset, size).stream()
                        .map(AppointmentConverter::toResponse)
                        .toList();
        long total = orderMapper.countOwnedPage(userId, serviceDate, status);
        return PageResponse.of(items, page, size, total);
    }

    @Override
    public AppointmentResponse getOwned(long userId, long appointmentId) {
        if (userId <= 0 || appointmentId <= 0) {
            throw new BusinessException(CommonErrorCode.VALIDATION);
        }
        AppointmentOrderEntity order = orderMapper.selectOwnedById(appointmentId, userId);
        if (order == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return AppointmentConverter.toResponse(settleExpired(order));
    }

    private AppointmentOrderEntity settleExpired(AppointmentOrderEntity order) {
        if (order.getStatus() == AppointmentStatus.PENDING_CONFIRM
                && !clock.instant().isBefore(order.getConfirmDeadline())) {
            stateService.expire(order.getId(), "query-expiry-" + order.getId());
            return orderMapper.selectById(order.getId());
        }
        return order;
    }

    private static void validatePage(long userId, int page, int size) {
        if (userId <= 0 || page < 1 || size < 1 || size > 100) {
            throw new BusinessException(CommonErrorCode.VALIDATION);
        }
    }
}
