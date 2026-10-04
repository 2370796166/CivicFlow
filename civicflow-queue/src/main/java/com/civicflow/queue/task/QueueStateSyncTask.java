package com.civicflow.queue.task;

import com.civicflow.queue.client.AppointmentQueueStateClient;
import com.civicflow.queue.config.QueueProperties;
import com.civicflow.queue.mapper.QueueStateSyncMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class QueueStateSyncTask {
    private final QueueStateSyncMapper mapper;
    private final AppointmentQueueStateClient appointment;
    private final QueueProperties properties;

    public QueueStateSyncTask(
            QueueStateSyncMapper mapper,
            AppointmentQueueStateClient appointment,
            QueueProperties properties) {
        this.mapper = mapper;
        this.appointment = appointment;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${civicflow.queue.state-sync-delay-ms:5000}")
    public void run() {
        if (!properties.isRecoveryEnabled()) return;
        for (var item : mapper.due(50)) {
            try {
                appointment.change(
                        item.appointmentId(),
                        new AppointmentQueueStateClient.Change(
                                item.ticketId().toString(),
                                item.targetStatus(),
                                item.id().toString()));
                mapper.done(item.id());
            } catch (RuntimeException ex) {
                mapper.defer(item.id(), ex.getClass().getSimpleName());
            }
        }
    }
}
