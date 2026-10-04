package com.civicflow.appointment.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.civicflow.appointment.config.AppointmentProperties;
import com.civicflow.appointment.entity.AppointmentReservationRequestEntity;
import com.civicflow.appointment.entity.StockReleaseRecordEntity;
import com.civicflow.appointment.enums.CompensationStatus;
import com.civicflow.appointment.mapper.AppointmentReservationRequestMapper;
import com.civicflow.appointment.mapper.StockReleaseRecordMapper;
import com.civicflow.appointment.service.StockReleaseService;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StockReleaseRecoveryTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(StockReleaseRecoveryTask.class);
    private final StockReleaseRecordMapper releases;
    private final AppointmentReservationRequestMapper requests;
    private final StockReleaseService service;
    private final AppointmentProperties properties;
    private final Clock clock;

    public StockReleaseRecoveryTask(
            StockReleaseRecordMapper releases,
            AppointmentReservationRequestMapper requests,
            StockReleaseService service,
            AppointmentProperties properties,
            Clock clock) {
        this.releases = releases;
        this.requests = requests;
        this.service = service;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${civicflow.appointment.timeout.release-retry-delay:10s}")
    public void retry() {
        if (!properties.getRecovery().isEnabled()) {
            return;
        }
        for (StockReleaseRecordEntity release :
                releases.selectList(
                        Wrappers.<StockReleaseRecordEntity>lambdaQuery()
                                .ne(
                                        StockReleaseRecordEntity::getStatus,
                                        CompensationStatus.SUCCEEDED)
                                .le(StockReleaseRecordEntity::getNextAttemptAt, clock.instant())
                                .orderByAsc(StockReleaseRecordEntity::getId)
                                .last("LIMIT " + properties.getRecovery().getBatchSize()))) {
            AppointmentReservationRequestEntity request =
                    requests.selectOne(
                            Wrappers.<AppointmentReservationRequestEntity>lambdaQuery()
                                    .eq(
                                            AppointmentReservationRequestEntity::getReservationId,
                                            release.getReservationId()));
            if (request == null) {
                LOGGER.error(
                        "Stock release request missing reservationId={}",
                        release.getReservationId());
                continue;
            }
            try {
                service.release(request, release.getReason());
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "Stock release retry pending reservationId={} type={}",
                        release.getReservationId(),
                        exception.getClass().getSimpleName());
            }
        }
    }
}
