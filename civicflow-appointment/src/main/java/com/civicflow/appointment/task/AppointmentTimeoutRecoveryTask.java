package com.civicflow.appointment.task;

import com.civicflow.appointment.config.AppointmentProperties;
import com.civicflow.appointment.mapper.AppointmentOrderMapper;
import com.civicflow.appointment.service.AppointmentStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AppointmentTimeoutRecoveryTask {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(AppointmentTimeoutRecoveryTask.class);
    private final AppointmentOrderMapper orders;
    private final AppointmentStateService states;
    private final AppointmentProperties properties;

    public AppointmentTimeoutRecoveryTask(
            AppointmentOrderMapper orders,
            AppointmentStateService states,
            AppointmentProperties properties) {
        this.orders = orders;
        this.states = states;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${civicflow.appointment.timeout.scan-delay:10s}")
    public void scan() {
        if (!properties.getRecovery().isEnabled()) {
            return;
        }
        for (Long id : orders.selectExpiredIds(properties.getRecovery().getBatchSize())) {
            try {
                states.expire(id, "timeout-scan-" + id);
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "Timeout recovery pending appointmentId={} type={}",
                        id,
                        exception.getClass().getSimpleName());
            }
        }
    }
}
