package com.civicflow.queue;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.civicflow.queue.client.AppointmentCheckInClient;
import com.civicflow.queue.config.QueueProperties;
import com.civicflow.queue.entity.CheckinReconciliationRecordEntity;
import com.civicflow.queue.mapper.CheckInReconciliationMapper;
import com.civicflow.queue.task.CheckInLinkRecoveryTask;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CheckInLinkRecoveryTest {
    @Test
    void failedLinkIsDeferredAndRetried() {
        var records = Mockito.mock(CheckInReconciliationMapper.class);
        var appointments = Mockito.mock(AppointmentCheckInClient.class);
        var row = new CheckinReconciliationRecordEntity();
        row.setId(1L);
        row.setAppointmentId(100L);
        row.setTicketId(200L);
        row.setClaimId("00000000-0000-0000-0000-000000000001");
        when(records.selectDue(50)).thenReturn(List.of(row));
        when(appointments.link(Mockito.eq(100L), Mockito.any()))
                .thenThrow(new IllegalStateException("offline"))
                .thenReturn(null);
        var task = new CheckInLinkRecoveryTask(records, appointments, new QueueProperties());
        task.recover();
        verify(records).defer(1L, "IllegalStateException");
        task.recover();
        verify(records).markLinked(1L);
    }
}
