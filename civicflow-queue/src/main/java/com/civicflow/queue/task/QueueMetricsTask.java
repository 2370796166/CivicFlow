package com.civicflow.queue.task;

import com.civicflow.queue.config.QueueProperties;
import io.micrometer.core.instrument.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class QueueMetricsTask {
    private final JdbcTemplate jdbc;
    private final QueueProperties properties;
    private final Map<String, AtomicLong> gauges = new HashMap<>();

    public QueueMetricsTask(JdbcTemplate jdbc, QueueProperties properties, MeterRegistry registry) {
        this.jdbc = jdbc;
        this.properties = properties;
        for (String status : List.of("PENDING", "PROCESSING", "NEEDS_ATTENTION")) {
            var value = new AtomicLong();
            gauges.put(status, value);
            Gauge.builder("civicflow.queue.sync.pending", value, AtomicLong::get)
                    .tag("status", status)
                    .register(registry);
        }
    }

    @Scheduled(fixedDelayString = "${civicflow.queue.metrics-delay:30s}")
    public void run() {
        if (!properties.isRecoveryEnabled()) return;
        try {
            Map<String, Long> counts = new HashMap<>();
            for (var row :
                    jdbc.queryForList(
                            "SELECT status,COUNT(*) AS total FROM queue_state_sync WHERE status!='DONE' GROUP BY status"))
                counts.put(row.get("status").toString(), ((Number) row.get("total")).longValue());
            gauges.forEach((status, value) -> value.set(counts.getOrDefault(status, 0L)));
        } catch (RuntimeException ex) {
            log.warn("Queue metrics refresh failed type={}", ex.getClass().getSimpleName());
        }
    }
}
