package com.civicflow.queue;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.civicflow.common.api.ApiResponse;
import com.civicflow.common.exception.BusinessException;
import com.civicflow.queue.client.AppointmentCheckInClient;
import com.civicflow.queue.client.ResourceStaffClient;
import com.civicflow.queue.mapper.CheckInReconciliationMapper;
import com.civicflow.queue.mapper.QueueTicketMapper;
import com.civicflow.queue.service.impl.CheckInServiceImpl;
import com.civicflow.queue.service.impl.QueueTicketCreationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CheckInAuthorizationTest {
    @Test
    void staffOutsideOutletCannotInvokeAppointmentClaim() {
        var appointments = Mockito.mock(AppointmentCheckInClient.class);
        var staff = Mockito.mock(ResourceStaffClient.class);
        var creation = Mockito.mock(QueueTicketCreationService.class);
        var tickets = Mockito.mock(QueueTicketMapper.class);
        var reconciliation = Mockito.mock(CheckInReconciliationMapper.class);
        when(staff.authorized(7001L, 3001L)).thenReturn(ApiResponse.success(false, "trace"));
        var service =
                new CheckInServiceImpl(appointments, staff, creation, tickets, reconciliation);
        assertThrows(
                BusinessException.class,
                () -> service.staffCheckIn(7001L, 3001L, "untrusted-token"));
        verifyNoInteractions(appointments, creation, tickets, reconciliation);
    }
}
