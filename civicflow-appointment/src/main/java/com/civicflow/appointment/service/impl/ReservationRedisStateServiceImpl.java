package com.civicflow.appointment.service.impl;

import com.civicflow.appointment.config.AppointmentProperties;
import com.civicflow.appointment.entity.AppointmentReservationRequestEntity;
import com.civicflow.appointment.service.ReservationRedisStateService;
import com.civicflow.appointment.support.LuaResult;
import com.civicflow.appointment.support.RedisStockRepository;
import com.civicflow.appointment.support.ReservationStateScriptResultCode;
import com.civicflow.appointment.support.SlotStockSnapshot;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class ReservationRedisStateServiceImpl implements ReservationRedisStateService {
    private final RedisStockRepository repository;
    private final AppointmentProperties properties;
    private final Clock clock;

    public ReservationRedisStateServiceImpl(
            RedisStockRepository repository, AppointmentProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public boolean markPublished(AppointmentReservationRequestEntity request) {
        Instant now = clock.instant();
        LuaResult result =
                repository.markPublished(
                        snapshot(request),
                        request.getUserId(),
                        request.getReservationId(),
                        request.getReservationId(),
                        now,
                        now.plus(properties.getStock().getReservationTtl()));
        ReservationStateScriptResultCode code =
                ReservationStateScriptResultCode.from(result.code());
        return code == ReservationStateScriptResultCode.UPDATED
                || code == ReservationStateScriptResultCode.IDEMPOTENT_OK;
    }

    @Override
    public boolean markPersisted(AppointmentReservationRequestEntity request, long appointmentId) {
        Instant now = clock.instant();
        LuaResult result =
                repository.markPersisted(
                        snapshot(request),
                        request.getUserId(),
                        request.getReservationId(),
                        appointmentId,
                        now,
                        request.getCloseAt().plus(properties.getStock().getRetentionAfterClose()));
        ReservationStateScriptResultCode code =
                ReservationStateScriptResultCode.from(result.code());
        return code == ReservationStateScriptResultCode.UPDATED
                || code == ReservationStateScriptResultCode.IDEMPOTENT_OK;
    }

    public static SlotStockSnapshot snapshot(AppointmentReservationRequestEntity request) {
        return new SlotStockSnapshot(
                request.getSlotId(),
                request.getOutletId(),
                request.getItemId(),
                request.getServiceDate(),
                request.getTotalQuota(),
                request.getReleaseAt(),
                request.getCloseAt(),
                request.getSlotStatus(),
                request.getSlotConfigVersion());
    }
}
