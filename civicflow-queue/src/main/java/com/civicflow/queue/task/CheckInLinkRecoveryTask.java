package com.civicflow.queue.task;

import com.civicflow.queue.client.AppointmentCheckInClient;
import com.civicflow.queue.config.QueueProperties;
import com.civicflow.queue.mapper.CheckInReconciliationMapper;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CheckInLinkRecoveryTask {
    private final CheckInReconciliationMapper records;
    private final AppointmentCheckInClient appointments;
    private final QueueProperties properties;

    public CheckInLinkRecoveryTask(
            CheckInReconciliationMapper records,
            AppointmentCheckInClient appointments,
            QueueProperties properties) {
        this.records = records;
        this.appointments = appointments;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${civicflow.queue.recovery-delay:30s}")
    public void recover() {
        if (!properties.isRecoveryEnabled()) {
            return;
        }
        for (var record : records.selectDue(50)) {
            try {
                appointments.link(
                        record.getAppointmentId(),
                        new AppointmentCheckInClient.LinkRequest(
                                record.getClaimId(),
                                record.getTicketId(),
                                UUID.randomUUID().toString()));
                records.markLinked(record.getId());
            } catch (RuntimeException exception) {
                records.defer(record.getId(), exception.getClass().getSimpleName());
            }
        }
    }
}
