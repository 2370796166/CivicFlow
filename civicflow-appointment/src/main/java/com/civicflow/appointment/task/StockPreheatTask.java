package com.civicflow.appointment.task;

import com.civicflow.appointment.config.AppointmentProperties;
import com.civicflow.appointment.service.StockPreheatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StockPreheatTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(StockPreheatTask.class);
    private final StockPreheatService service;
    private final AppointmentProperties properties;

    public StockPreheatTask(StockPreheatService service, AppointmentProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${civicflow.appointment.preheat.fixed-delay:60s}")
    public void preheat() {
        if (!properties.getPreheat().isEnabled()) {
            return;
        }
        try {
            StockPreheatService.PreheatBatchResult result = service.preheatWindow();
            LOGGER.info(
                    "Slot preheat completed visited={} applied={} unchanged={} failed={}",
                    result.visited(),
                    result.applied(),
                    result.unchanged(),
                    result.failed());
        } catch (RuntimeException exception) {
            LOGGER.error("Slot preheat task failed type={}", exception.getClass().getSimpleName());
        }
    }
}
