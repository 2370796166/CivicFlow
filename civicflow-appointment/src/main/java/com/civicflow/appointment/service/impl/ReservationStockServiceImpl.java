package com.civicflow.appointment.service.impl;

import com.civicflow.appointment.config.AppointmentProperties;
import com.civicflow.appointment.error.AppointmentErrorCode;
import com.civicflow.appointment.service.ReservationStockService;
import com.civicflow.appointment.support.CompensationScriptResultCode;
import com.civicflow.appointment.support.LuaResult;
import com.civicflow.appointment.support.RedisStockRepository;
import com.civicflow.appointment.support.SlotStockSnapshot;
import com.civicflow.appointment.support.StockScriptResultCode;
import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.stereotype.Service;

@Service
public class ReservationStockServiceImpl implements ReservationStockService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final long MINIMUM_COMPENSATION_MARKER_HOURS = 48;

    private final RedisStockRepository repository;
    private final AppointmentProperties properties;
    private final Clock clock;

    public ReservationStockServiceImpl(
            RedisStockRepository repository, AppointmentProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public ReservationResult reserve(
            SlotStockSnapshot snapshot, long userId, String reservationId) {
        if (userId <= 0 || reservationId == null || reservationId.isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION);
        }
        Instant now = clock.instant();
        Instant reservationExpiresAt = now.plus(properties.getStock().getReservationTtl());
        Instant activeExpiresAt =
                snapshot.serviceDate().plusDays(3).atStartOfDay(BUSINESS_ZONE).toInstant();
        LuaResult result =
                repository.reserve(
                        snapshot,
                        userId,
                        reservationId,
                        now,
                        reservationExpiresAt,
                        activeExpiresAt);
        StockScriptResultCode code = StockScriptResultCode.from(result.code());
        return switch (code) {
            case OK ->
                    new ReservationResult(
                            reservationId, false, result.remaining(), result.configVersion());
            case IDEMPOTENT_OK ->
                    new ReservationResult(
                            reservationId, true, result.remaining(), result.configVersion());
            case DUP_ACTIVE -> throw new BusinessException(AppointmentErrorCode.DUP_ACTIVE);
            case OUT_OF_STOCK -> throw new BusinessException(AppointmentErrorCode.STOCK_EMPTY);
            case SLOT_NOT_FOUND, NOT_RELEASED, SLOT_NOT_OPEN, SLOT_CLOSED ->
                    throw new BusinessException(AppointmentErrorCode.SLOT_NOT_OPEN);
            case RESERVATION_CONFLICT ->
                    throw new BusinessException(CommonErrorCode.IDEMPOTENCY_CONFLICT);
            case CONFIG_VERSION_MISMATCH, ACTIVE_WRITE_FAILED ->
                    throw new BusinessException(AppointmentErrorCode.STATE_CONFLICT);
        };
    }

    @Override
    public CompensationResult compensate(
            SlotStockSnapshot snapshot, long userId, String reservationId, String reason) {
        if (userId <= 0
                || reservationId == null
                || reservationId.isBlank()
                || reason == null
                || reason.isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION);
        }
        Instant now = clock.instant();
        Instant stockRetention =
                snapshot.closeAt().plus(properties.getStock().getRetentionAfterClose());
        Instant minimumMarker = now.plusSeconds(MINIMUM_COMPENSATION_MARKER_HOURS * 3600);
        Instant markerExpiresAt =
                stockRetention.isAfter(minimumMarker) ? stockRetention : minimumMarker;
        LuaResult result =
                repository.compensate(
                        snapshot, userId, reservationId, reason, now, markerExpiresAt);
        CompensationScriptResultCode code = CompensationScriptResultCode.from(result.code());
        return switch (code) {
            case RELEASED ->
                    new CompensationResult(false, result.remaining(), result.configVersion());
            case ALREADY_RELEASED ->
                    new CompensationResult(true, result.remaining(), result.configVersion());
            case NEED_RECONCILE, INVARIANT_BROKEN ->
                    throw new BusinessException(AppointmentErrorCode.STOCK_INVARIANT_BROKEN);
            case OCCUPANCY_MISMATCH, RESERVATION_CONFLICT ->
                    throw new BusinessException(AppointmentErrorCode.STATE_CONFLICT);
        };
    }
}
