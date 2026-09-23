package com.civicflow.appointment.task;

import com.civicflow.appointment.config.AppointmentProperties;
import com.civicflow.appointment.entity.AppointmentReservationRequestEntity;
import com.civicflow.appointment.mapper.AppointmentReservationRequestMapper;
import com.civicflow.appointment.service.ReservationWorkflowService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ReservationRecoveryTask {
    private final AppointmentReservationRequestMapper requestMapper;
    private final ReservationWorkflowService workflowService;
    private final AppointmentProperties properties;
    private final Clock clock;
    private final String owner = UUID.randomUUID().toString();

    public ReservationRecoveryTask(
            AppointmentReservationRequestMapper requestMapper,
            ReservationWorkflowService workflowService,
            AppointmentProperties properties,
            Clock clock) {
        this.requestMapper = requestMapper;
        this.workflowService = workflowService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${civicflow.appointment.recovery.fixed-delay:10s}")
    public void recover() {
        if (!properties.getRecovery().isEnabled()) {
            return;
        }
        Instant now = clock.instant();
        List<AppointmentReservationRequestEntity> candidates =
                requestMapper.selectRecoverable(now, properties.getRecovery().getBatchSize());
        for (AppointmentReservationRequestEntity candidate : candidates) {
            Instant leaseUntil = now.plus(properties.getRecovery().getLease());
            if (requestMapper.claimRecovery(candidate.getId(), owner, now, leaseUntil) != 1) {
                continue;
            }
            try {
                workflowService.recover(candidate.getId());
            } catch (RuntimeException exception) {
                log.warn(
                        "Reservation recovery failed reservationId={} exceptionType={}",
                        candidate.getReservationId(),
                        exception.getClass().getSimpleName());
            }
        }
    }
}
