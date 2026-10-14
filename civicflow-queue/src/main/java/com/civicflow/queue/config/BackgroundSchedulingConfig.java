package com.civicflow.queue.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Notification broker waits cannot occupy recovery scheduler threads. */
@Configuration
public class BackgroundSchedulingConfig {
    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler recoveryScheduler(
            @Value("${spring.task.scheduling.pool.size:4}") int poolSize) {
        return scheduler("queue-recovery-", poolSize);
    }

    @Bean(name = "notificationScheduler")
    public ThreadPoolTaskScheduler notificationScheduler() {
        return scheduler("queue-notification-", 1);
    }

    private static ThreadPoolTaskScheduler scheduler(String prefix, int size) {
        if (size < 1) throw new IllegalArgumentException("Scheduler pool must be positive");
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(size);
        scheduler.setThreadNamePrefix(prefix);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setAwaitTerminationSeconds(5);
        return scheduler;
    }
}
