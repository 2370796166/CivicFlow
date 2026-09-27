package com.civicflow.appointment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.civicflow.appointment.entity.ActiveBookingGuardEntity;
import com.civicflow.appointment.entity.AppointmentOperationLogEntity;
import com.civicflow.appointment.enums.AppointmentActorType;
import com.civicflow.appointment.enums.AppointmentOperation;
import com.civicflow.appointment.enums.AppointmentStatus;
import com.civicflow.appointment.error.AppointmentErrorCode;
import com.civicflow.appointment.mapper.ActiveBookingGuardMapper;
import com.civicflow.appointment.mapper.AppointmentOperationLogMapper;
import com.civicflow.appointment.mapper.AppointmentOrderMapper;
import com.civicflow.appointment.service.QueueStateSyncService;
import com.civicflow.appointment.support.RedisStockRepository;
import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.exception.BusinessException;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class QueueStateSyncServiceImpl implements QueueStateSyncService {
    private final AppointmentOrderMapper orders;
    private final ActiveBookingGuardMapper guards;
    private final AppointmentOperationLogMapper logs;
    private final RedisStockRepository stock;

    public QueueStateSyncServiceImpl(
            AppointmentOrderMapper orders,
            ActiveBookingGuardMapper guards,
            AppointmentOperationLogMapper logs,
            RedisStockRepository stock) {
        this.orders = orders;
        this.guards = guards;
        this.logs = logs;
        this.stock = stock;
    }

    @Override
    @Transactional
    public void apply(
            long appointmentId,
            long ticketId,
            String queueStatus,
            String syncId,
            String requestId) {
        AppointmentStatus from;
        AppointmentStatus to;
        AppointmentOperation operation;
        switch (queueStatus) {
            case "SERVING" -> {
                from = AppointmentStatus.CHECKED_IN;
                to = AppointmentStatus.SERVING;
                operation = AppointmentOperation.START_SERVING;
            }
            case "COMPLETED" -> {
                from = AppointmentStatus.SERVING;
                to = AppointmentStatus.COMPLETED;
                operation = AppointmentOperation.COMPLETE;
            }
            case "MISSED" -> {
                from = AppointmentStatus.CHECKED_IN;
                to = AppointmentStatus.NO_SHOW;
                operation = AppointmentOperation.MARK_NO_SHOW;
            }
            default -> throw new BusinessException(CommonErrorCode.VALIDATION);
        }
        var order = orders.selectById(appointmentId);
        if (order == null || !Objects.equals(order.getQueueTicketId(), ticketId))
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        if (order.getStatus() == to) {
            if (to == AppointmentStatus.COMPLETED || to == AppointmentStatus.NO_SHOW) {
                releaseActiveAfterCommit(order);
            }
            return;
        }
        if (order.getStatus() != from
                || orders.applyQueueState(appointmentId, ticketId, from.name(), to.name()) != 1)
            throw new BusinessException(AppointmentErrorCode.STATE_CONFLICT);
        if (to == AppointmentStatus.COMPLETED || to == AppointmentStatus.NO_SHOW) {
            guards.delete(
                    new LambdaQueryWrapper<ActiveBookingGuardEntity>()
                            .eq(ActiveBookingGuardEntity::getAppointmentId, appointmentId));
        }
        AppointmentOperationLogEntity log = new AppointmentOperationLogEntity();
        log.setId(IdWorker.getId());
        log.setAppointmentId(appointmentId);
        log.setReservationId(order.getReservationId());
        log.setActorType(AppointmentActorType.SERVICE);
        log.setActorId(0L);
        log.setOperation(operation);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setRequestId(requestId);
        log.setOccurredAt(Instant.now());
        log.setDetailJson("{\"syncId\":\"" + syncId + "\"}");
        logs.insert(log);
        if (to == AppointmentStatus.COMPLETED || to == AppointmentStatus.NO_SHOW) {
            releaseActiveAfterCommit(order);
        }
    }

    private void releaseActiveAfterCommit(
            com.civicflow.appointment.entity.AppointmentOrderEntity order) {
        Runnable release =
                () ->
                        stock.releaseActiveIfOwned(
                                order.getItemId(),
                                order.getServiceDate(),
                                order.getUserId(),
                                order.getReservationId());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            release.run();
                        }
                    });
        } else {
            release.run();
        }
    }
}
