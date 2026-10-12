package com.civicflow.appointment.support;

import com.civicflow.appointment.config.AppointmentProperties;
import io.micrometer.core.instrument.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AppointmentMetrics {
    private final JdbcTemplate jdbc;
    private final AppointmentProperties properties;
    private final Map<String, AtomicLong> gauges = new HashMap<>();
    private final AtomicLong refreshedAt = new AtomicLong();

    public AppointmentMetrics(
            JdbcTemplate jdbc, AppointmentProperties properties, MeterRegistry registry) {
        this.jdbc = jdbc;
        this.properties = properties;
        for (String status :
                List.of("CREATED", "RESERVED", "PUBLISH_UNKNOWN", "COMPENSATION_PENDING")) {
            var value = new AtomicLong();
            gauges.put(status, value);
            Gauge.builder("civicflow.reservation.pending", value, AtomicLong::get)
                    .tag("status", status)
                    .register(registry);
        }
        for (String kind : List.of("outbox", "stock_release", "active_release")) {
            var value = new AtomicLong();
            gauges.put(kind, value);
            Gauge.builder("civicflow.recovery.pending", value, AtomicLong::get)
                    .tag("kind", kind)
                    .register(registry);
        }
        var oldest = new AtomicLong();
        gauges.put("oldest", oldest);
        Gauge.builder("civicflow.reservation.oldest.seconds", oldest, AtomicLong::get)
                .register(registry);
        Gauge.builder("civicflow.metrics.refreshed.timestamp", refreshedAt, AtomicLong::get)
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${civicflow.appointment.metrics-delay:30s}")
    public void refresh() {
        if (!properties.getRecovery().isEnabled()) return;
        try {
            var rows =
                    jdbc.queryForList(
                            "SELECT status,COUNT(*) AS total FROM appointment_reservation_request WHERE status IN ('CREATED','RESERVED','PUBLISH_UNKNOWN','COMPENSATION_PENDING') GROUP BY status");
            Map<String, Long> counts = new HashMap<>();
            for (var row : rows)
                counts.put(row.get("status").toString(), ((Number) row.get("total")).longValue());
            for (String status :
                    List.of("CREATED", "RESERVED", "PUBLISH_UNKNOWN", "COMPENSATION_PENDING"))
                gauges.get(status).set(counts.getOrDefault(status, 0L));
            gauges.get("outbox")
                    .set(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM outbox_event WHERE status!='PUBLISHED'",
                                    Long.class));
            gauges.get("stock_release")
                    .set(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM stock_release_record WHERE status!='SUCCEEDED'",
                                    Long.class));
            gauges.get("active_release")
                    .set(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM appointment_active_release WHERE status='PENDING'",
                                    Long.class));
            var oldest =
                    jdbc.queryForObject(
                            "SELECT MIN(created_at) FROM appointment_reservation_request WHERE status IN ('CREATED','RESERVED','PUBLISH_UNKNOWN','COMPENSATION_PENDING')",
                            java.sql.Timestamp.class);
            gauges.get("oldest")
                    .set(
                            oldest == null
                                    ? 0
                                    : Math.max(
                                            0,
                                            java.time.Duration.between(
                                                            oldest.toInstant(),
                                                            java.time.Instant.now())
                                                    .toSeconds()));
            refreshedAt.set(java.time.Instant.now().getEpochSecond());
        } catch (RuntimeException ex) {
            log.warn("Appointment metrics refresh failed type={}", ex.getClass().getSimpleName());
        }
    }
}
