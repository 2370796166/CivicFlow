package com.civicflow.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.civicflow.appointment.entity.AppointmentOrderEntity;
import com.civicflow.appointment.enums.AppointmentStatus;
import com.civicflow.appointment.mapper.ActiveBookingGuardMapper;
import com.civicflow.appointment.mapper.AppointmentOperationLogMapper;
import com.civicflow.appointment.mapper.AppointmentOrderMapper;
import com.civicflow.appointment.service.impl.QueueStateSyncServiceImpl;
import com.civicflow.appointment.support.RedisStockRepository;
import org.junit.jupiter.api.Test;

class QueueStateSyncServiceTest {
    @Test
    void duplicateCompletionOnlyWritesOnce() {
        AppointmentOrderMapper orders = mock(AppointmentOrderMapper.class);
        ActiveBookingGuardMapper guards = mock(ActiveBookingGuardMapper.class);
        AppointmentOperationLogMapper logs = mock(AppointmentOperationLogMapper.class);
        RedisStockRepository stock = mock(RedisStockRepository.class);
        var service = new QueueStateSyncServiceImpl(orders, guards, logs, stock);
        var order = new AppointmentOrderEntity();
        order.setId(100L);
        order.setQueueTicketId(200L);
        order.setReservationId("reservation");
        order.setItemId(300L);
        order.setUserId(400L);
        order.setServiceDate(java.time.LocalDate.of(2026, 9, 25));
        order.setStatus(AppointmentStatus.SERVING);
        when(orders.selectById(100L)).thenReturn(order);
        when(orders.applyQueueState(100L, 200L, "SERVING", "COMPLETED"))
                .thenAnswer(
                        invocation -> {
                            order.setStatus(AppointmentStatus.COMPLETED);
                            return 1;
                        });
        service.apply(100, 200, "COMPLETED", "sync-1", "request-1");
        service.apply(100, 200, "COMPLETED", "sync-1", "request-1");
        assertEquals(AppointmentStatus.COMPLETED, order.getStatus());
        verify(orders, times(1)).applyQueueState(100L, 200L, "SERVING", "COMPLETED");
        verify(logs, times(1))
                .insert(any(com.civicflow.appointment.entity.AppointmentOperationLogEntity.class));
        verify(guards, times(1))
                .delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        verify(stock, times(2)).releaseActiveIfOwned(anyLong(), any(), anyLong(), any());
    }
}
