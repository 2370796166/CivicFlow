package com.civicflow.appointment.task;

import com.civicflow.appointment.config.AppointmentProperties;
import com.civicflow.appointment.service.StockReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StockReconciliationTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(StockReconciliationTask.class);
    private final StockReconciliationService service;
    private final AppointmentProperties properties;

    public StockReconciliationTask(
            StockReconciliationService service, AppointmentProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${civicflow.appointment.reconciliation.fixed-delay:5m}")
    public void scan() {
        if (!properties.getReconciliation().isEnabled()) {
            return;
        }
        try {
            StockReconciliationService.BatchReport result = service.reconcileWindow();
            LOGGER.info(
                    "Stock reconciliation scanned={} consistent={} observed={} repaired={} alerted={} failed={}",
                    result.visited(),
                    result.consistent(),
                    result.observed(),
                    result.repaired(),
                    result.alerted(),
                    result.failed());
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Stock reconciliation scan failed type={}",
                    exception.getClass().getSimpleName());
        }
    }
}
