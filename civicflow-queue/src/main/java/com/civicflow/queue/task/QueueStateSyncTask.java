package com.civicflow.queue.task;

import com.civicflow.queue.client.AppointmentQueueStateClient;
import com.civicflow.queue.config.QueueProperties;
import com.civicflow.queue.mapper.QueueStateSyncMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class QueueStateSyncTask {
    private final QueueStateSyncMapper mapper;
    private final AppointmentQueueStateClient appointment;
    private final QueueProperties properties;
    private final String owner = UUID.randomUUID().toString();

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
            if (mapper.claim(item.id(), owner, Instant.now().plusSeconds(30)) != 1) continue;
            try {
                appointment.change(
                        item.appointmentId(),
                        new AppointmentQueueStateClient.Change(
                                item.ticketId().toString(),
                                item.targetStatus(),
                                item.id().toString()));
                mapper.done(item.id(), owner);
            } catch (RuntimeException ex) {
                // Authentication failures may be resolved by short-lived service-token rotation.
                int status = ex instanceof feign.FeignException feign ? feign.status() : 0;
                boolean permanent = status == 400 || status == 404 || status == 422;
                boolean exhausted = item.attempts() + 1 >= properties.getSyncMaxAttempts();
                long delay = Math.min(600, 5L << Math.min(item.attempts(), 7));
                mapper.defer(
                        item.id(),
                        owner,
                        permanent || exhausted ? "NEEDS_ATTENTION" : "PENDING",
                        Instant.now().plusSeconds(delay),
                        ex.getClass().getSimpleName());
            }
        }
    }
}
