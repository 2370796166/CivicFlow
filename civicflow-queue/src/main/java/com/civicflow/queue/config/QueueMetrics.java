package com.civicflow.queue.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.civicflow.queue.entity.QueueTicketEntity;
import com.civicflow.queue.enums.QueueTicketStatus;
import com.civicflow.queue.mapper.QueueTicketMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueueMetrics {
    @Bean
    MeterBinder waitingTickets(QueueTicketMapper tickets) {
        return registry ->
                Gauge.builder(
                                "civicflow_queue_waiting",
                                tickets,
                                mapper -> {
                                    try {
                                        return mapper.selectCount(
                                                Wrappers.<QueueTicketEntity>lambdaQuery()
                                                        .eq(
                                                                QueueTicketEntity::getStatus,
                                                                QueueTicketStatus.WAITING));
                                    } catch (RuntimeException exception) {
                                        return Double.NaN;
                                    }
                                })
                        .description("Current WAITING queue tickets across all outlets")
                        .register(registry);
    }
}
