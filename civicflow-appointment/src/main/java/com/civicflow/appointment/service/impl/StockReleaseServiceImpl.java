package com.civicflow.appointment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.civicflow.appointment.config.AppointmentProperties;
import com.civicflow.appointment.entity.AppointmentReservationRequestEntity;
import com.civicflow.appointment.entity.StockReleaseRecordEntity;
import com.civicflow.appointment.enums.CompensationStatus;
import com.civicflow.appointment.enums.StockReleaseReason;
import com.civicflow.appointment.mapper.StockReleaseRecordMapper;
import com.civicflow.appointment.service.ReservationStockService;
import com.civicflow.appointment.service.RetryableStockCompensationException;
import com.civicflow.appointment.service.StockReleaseService;
import com.civicflow.common.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockReleaseServiceImpl implements StockReleaseService {
    private final StockReleaseRecordMapper mapper;
    private final ReservationStockService stockService;
    private final Clock clock;
    private final AppointmentProperties properties;

    public StockReleaseServiceImpl(
            StockReleaseRecordMapper mapper,
            ReservationStockService stockService,
            Clock clock,
            AppointmentProperties properties) {
        this.mapper = mapper;
        this.stockService = stockService;
        this.clock = clock;
        this.properties = properties;
    }

    @Override
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = RetryableStockCompensationException.class)
    public void release(AppointmentReservationRequestEntity request, StockReleaseReason reason) {
        StockReleaseRecordEntity record = find(request.getReservationId());
        if (record != null && record.getStatus() == CompensationStatus.SUCCEEDED) {
            return;
        }
        if (record == null) {
            record = new StockReleaseRecordEntity();
            record.setReservationId(request.getReservationId());
            record.setSlotId(request.getSlotId());
            record.setUserId(request.getUserId());
            record.setReason(reason);
            record.setStatus(CompensationStatus.PENDING);
            record.setAttempts(0);
            record.setNextAttemptAt(clock.instant());
            try {
                mapper.insert(record);
            } catch (DuplicateKeyException exception) {
                record = find(request.getReservationId());
            }
        }
        if (record == null) {
            throw new IllegalStateException("Compensation record could not be loaded");
        }
        Instant now = clock.instant();
        record.setAttempts(record.getAttempts() + 1);
        record.setNextAttemptAt(now);
        record.setStatus(CompensationStatus.PENDING);
        mapper.updateById(record);
        try {
            stockService.compensate(
                    ReservationRedisStateServiceImpl.snapshot(request),
                    request.getUserId(),
                    request.getReservationId(),
                    reason.name());
            record.setStatus(CompensationStatus.SUCCEEDED);
            record.setReleasedAt(now);
            record.setLastErrorCode(null);
            mapper.updateById(record);
        } catch (BusinessException exception) {
            record.setStatus(CompensationStatus.NEED_RECONCILE);
            record.setNextAttemptAt(now.plus(properties.getRecovery().getRetryDelay()));
            record.setLastErrorCode(exception.errorCode().code());
            mapper.updateById(record);
            throw new RetryableStockCompensationException(
                    "Stock release requires retry or reconciliation", exception);
        } catch (RuntimeException exception) {
            record.setStatus(CompensationStatus.FAILED);
            record.setNextAttemptAt(now.plus(properties.getRecovery().getRetryDelay()));
            record.setLastErrorCode("COMPENSATION_DEPENDENCY_FAILED");
            mapper.updateById(record);
            throw new RetryableStockCompensationException(
                    "Stock release dependency failed", exception);
        }
    }

    private StockReleaseRecordEntity find(String reservationId) {
        return mapper.selectOne(
                Wrappers.<StockReleaseRecordEntity>lambdaQuery()
                        .eq(StockReleaseRecordEntity::getReservationId, reservationId));
    }
}
