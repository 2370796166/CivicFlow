package com.civicflow.queue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.civicflow.queue.client.AppointmentQueueStateClient;
import com.civicflow.queue.config.QueueProperties;
import com.civicflow.queue.mapper.QueueStateSyncMapper;
import com.civicflow.queue.task.QueueStateSyncTask;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueueStateSyncTaskTest {
    @Test
    void remoteFailureRemainsPendingAndNextAttemptCompletes() {
        QueueStateSyncMapper mapper = mock(QueueStateSyncMapper.class);
        AppointmentQueueStateClient appointment = mock(AppointmentQueueStateClient.class);
        QueueProperties properties = new QueueProperties();
        var pending = new QueueStateSyncMapper.Pending(1L, 2L, 3L, "COMPLETED");
        when(mapper.due(50)).thenReturn(List.of(pending));
        when(appointment.change(eq(3L), any(AppointmentQueueStateClient.Change.class)))
                .thenThrow(new IllegalStateException("unavailable"))
                .thenReturn(null);
        QueueStateSyncTask task = new QueueStateSyncTask(mapper, appointment, properties);
        task.run();
        verify(mapper).defer(1L, "IllegalStateException");
        verify(mapper, never()).done(1L);
        task.run();
        verify(mapper).done(1L);
    }
}
