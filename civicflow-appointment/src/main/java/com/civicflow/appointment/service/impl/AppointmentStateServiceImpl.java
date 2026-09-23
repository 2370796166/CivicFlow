package com.civicflow.appointment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.civicflow.appointment.convert.AppointmentConverter;
import com.civicflow.appointment.dto.response.AppointmentResponse;
import com.civicflow.appointment.entity.AppointmentCommandIdempotencyEntity;
import com.civicflow.appointment.entity.AppointmentOperationLogEntity;
import com.civicflow.appointment.entity.AppointmentOrderEntity;
import com.civicflow.appointment.entity.AppointmentReservationRequestEntity;
import com.civicflow.appointment.entity.StockReleaseRecordEntity;
import com.civicflow.appointment.enums.AppointmentActorType;
import com.civicflow.appointment.enums.AppointmentOperation;
import com.civicflow.appointment.enums.AppointmentStatus;
import com.civicflow.appointment.enums.CompensationStatus;
import com.civicflow.appointment.enums.StockReleaseReason;
import com.civicflow.appointment.error.AppointmentErrorCode;
import com.civicflow.appointment.mapper.ActiveBookingGuardMapper;
import com.civicflow.appointment.mapper.AppointmentCommandIdempotencyMapper;
import com.civicflow.appointment.mapper.AppointmentOperationLogMapper;
import com.civicflow.appointment.mapper.AppointmentOrderMapper;
import com.civicflow.appointment.mapper.AppointmentReservationRequestMapper;
import com.civicflow.appointment.mapper.StockReleaseRecordMapper;
import com.civicflow.appointment.service.AppointmentStateService;
import com.civicflow.appointment.service.StockReleaseService;
import com.civicflow.appointment.support.Digests;
import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.exception.BusinessException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AppointmentStateServiceImpl implements AppointmentStateService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppointmentStateServiceImpl.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final AppointmentOrderMapper orders;
    private final AppointmentCommandIdempotencyMapper commands;
    private final AppointmentOperationLogMapper logs;
    private final ActiveBookingGuardMapper guards;
    private final StockReleaseRecordMapper releases;
    private final AppointmentReservationRequestMapper requests;
    private final StockReleaseService stockRelease;

    public AppointmentStateServiceImpl(
            AppointmentOrderMapper orders,
            AppointmentCommandIdempotencyMapper commands,
            AppointmentOperationLogMapper logs,
            ActiveBookingGuardMapper guards,
            StockReleaseRecordMapper releases,
            AppointmentReservationRequestMapper requests,
            StockReleaseService stockRelease) {
        this.orders = orders;
        this.commands = commands;
        this.logs = logs;
        this.guards = guards;
        this.releases = releases;
        this.requests = requests;
        this.stockRelease = stockRelease;
    }

    @Override
    @Transactional
    public AppointmentResponse confirm(
            long userId, long appointmentId, int version, String key, String traceId) {
        validate(userId, appointmentId, version, key);
        AppointmentOrderEntity before = owned(userId, appointmentId);
        bind(
                userId,
                appointmentId,
                AppointmentOperation.CONFIRM.name(),
                key,
                "appointmentId=" + appointmentId + ";version=" + version);
        if (before.getStatus() == AppointmentStatus.CONFIRMED) {
            return AppointmentConverter.toResponse(before);
        }
        if (before.getStatus() != AppointmentStatus.PENDING_CONFIRM) {
            throw new BusinessException(AppointmentErrorCode.STATE_CONFLICT);
        }
        if (orders.confirm(appointmentId, userId, version) != 1) {
            AppointmentOrderEntity current = owned(userId, appointmentId);
            if (current.getStatus() == AppointmentStatus.CONFIRMED) {
                return AppointmentConverter.toResponse(current);
            }
            throw new BusinessException(
                    current.getStatus() == AppointmentStatus.PENDING_CONFIRM
                            ? AppointmentErrorCode.CONFIRM_TIMEOUT
                            : AppointmentErrorCode.STATE_CONFLICT);
        }
        log(
                before,
                AppointmentStatus.CONFIRMED,
                AppointmentOperation.CONFIRM,
                AppointmentActorType.USER,
                userId,
                "USER_CONFIRM",
                traceId);
        return AppointmentConverter.toResponse(owned(userId, appointmentId));
    }

    @Override
    @Transactional
    public AppointmentResponse cancel(
            long userId,
            long appointmentId,
            int version,
            String reason,
            String key,
            String traceId) {
        validate(userId, appointmentId, version, key);
        if (reason == null || reason.isBlank() || reason.length() > 256) {
            throw new BusinessException(CommonErrorCode.VALIDATION);
        }
        AppointmentOrderEntity before = owned(userId, appointmentId);
        bind(
                userId,
                appointmentId,
                AppointmentOperation.CANCEL.name(),
                key,
                "appointmentId="
                        + appointmentId
                        + ";version="
                        + version
                        + ";reason="
                        + reason.trim());
        if (before.getStatus() == AppointmentStatus.CANCELLED
                || before.getStatus() == AppointmentStatus.EXPIRED) {
            return AppointmentConverter.toResponse(before);
        }
        if (before.getStatus() != AppointmentStatus.PENDING_CONFIRM
                && before.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BusinessException(AppointmentErrorCode.STATE_CONFLICT);
        }
        Instant cutoff =
                LocalDateTime.of(before.getServiceDate(), before.getSlotStartTime())
                        .atZone(BUSINESS_ZONE)
                        .toInstant();
        if (orders.cancel(appointmentId, userId, version, reason.trim(), cutoff) != 1) {
            AppointmentOrderEntity current = owned(userId, appointmentId);
            if (current.getStatus() == AppointmentStatus.CANCELLED
                    || current.getStatus() == AppointmentStatus.EXPIRED) {
                return AppointmentConverter.toResponse(current);
            }
            if (current.getStatus() == AppointmentStatus.PENDING_CONFIRM) {
                expire(appointmentId, traceId);
                current = owned(userId, appointmentId);
                if (current.getStatus() == AppointmentStatus.EXPIRED) {
                    return AppointmentConverter.toResponse(current);
                }
            }
            throw new BusinessException(AppointmentErrorCode.STATE_CONFLICT);
        }
        finish(
                before,
                AppointmentStatus.CANCELLED,
                AppointmentOperation.CANCEL,
                AppointmentActorType.USER,
                userId,
                "USER_CANCELLED",
                traceId,
                StockReleaseReason.USER_CANCELLED);
        return AppointmentConverter.toResponse(owned(userId, appointmentId));
    }

    @Override
    @Transactional
    public void expire(long appointmentId, String traceId) {
        AppointmentOrderEntity before = orders.selectById(appointmentId);
        if (before == null || before.getStatus() != AppointmentStatus.PENDING_CONFIRM) {
            return;
        }
        if (orders.expire(appointmentId) != 1) {
            return;
        }
        finish(
                before,
                AppointmentStatus.EXPIRED,
                AppointmentOperation.EXPIRE,
                AppointmentActorType.SYSTEM,
                null,
                "CONFIRM_DEADLINE_ELAPSED",
                traceId,
                StockReleaseReason.APPOINTMENT_EXPIRED);
    }

    private void finish(
            AppointmentOrderEntity before,
            AppointmentStatus target,
            AppointmentOperation operation,
            AppointmentActorType actorType,
            Long actorId,
            String reason,
            String traceId,
            StockReleaseReason releaseReason) {
        guards.delete(
                Wrappers.<com.civicflow.appointment.entity.ActiveBookingGuardEntity>lambdaQuery()
                        .eq(
                                com.civicflow.appointment.entity.ActiveBookingGuardEntity
                                        ::getAppointmentId,
                                before.getId()));
        StockReleaseRecordEntity record = new StockReleaseRecordEntity();
        record.setReservationId(before.getReservationId());
        record.setSlotId(before.getSlotId());
        record.setUserId(before.getUserId());
        record.setReason(releaseReason);
        record.setStatus(CompensationStatus.PENDING);
        record.setAttempts(0);
        record.setNextAttemptAt(Instant.now());
        releases.insert(record);
        log(before, target, operation, actorType, actorId, reason, traceId);
        AppointmentReservationRequestEntity request =
                requests.selectOne(
                        Wrappers.<AppointmentReservationRequestEntity>lambdaQuery()
                                .eq(
                                        AppointmentReservationRequestEntity::getReservationId,
                                        before.getReservationId()));
        if (request == null) {
            throw new IllegalStateException("Reservation request missing for stock release");
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            stockRelease.release(request, releaseReason);
                        } catch (RuntimeException exception) {
                            LOGGER.warn(
                                    "Stock release deferred reservationId={} type={}",
                                    before.getReservationId(),
                                    exception.getClass().getSimpleName());
                        }
                    }
                });
    }

    private void log(
            AppointmentOrderEntity before,
            AppointmentStatus target,
            AppointmentOperation operation,
            AppointmentActorType actorType,
            Long actorId,
            String reason,
            String traceId) {
        AppointmentOperationLogEntity entry = new AppointmentOperationLogEntity();
        entry.setAppointmentId(before.getId());
        entry.setReservationId(before.getReservationId());
        entry.setActorType(actorType);
        entry.setActorId(actorId == null ? 0L : actorId);
        entry.setOperation(operation);
        entry.setFromStatus(before.getStatus());
        entry.setToStatus(target);
        entry.setRequestId(traceId);
        entry.setOccurredAt(Instant.now());
        entry.setDetailJson(
                "{\"reason\":\"" + reason.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
        logs.insert(entry);
    }

    private AppointmentOrderEntity owned(long userId, long appointmentId) {
        AppointmentOrderEntity order = orders.selectOwnedById(appointmentId, userId);
        if (order == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return order;
    }

    private void bind(
            long userId, long appointmentId, String operation, String key, String payload) {
        byte[] keyHash = Digests.sha256(key);
        byte[] payloadHash = Digests.sha256(payload);
        commands.insertIgnore(
                IdWorker.getId(), userId, appointmentId, operation, keyHash, payloadHash);
        AppointmentCommandIdempotencyEntity bound =
                commands.selectLocked(userId, operation, keyHash);
        if (bound == null
                || bound.getAppointmentId() != appointmentId
                || !Digests.equal(bound.getPayloadHash(), payloadHash)) {
            throw new BusinessException(CommonErrorCode.IDEMPOTENCY_CONFLICT);
        }
    }

    private static void validate(long userId, long appointmentId, int version, String key) {
        if (userId <= 0
                || appointmentId <= 0
                || version < 0
                || key == null
                || key.isBlank()
                || key.length() > 128) {
            throw new BusinessException(CommonErrorCode.VALIDATION);
        }
    }
}
