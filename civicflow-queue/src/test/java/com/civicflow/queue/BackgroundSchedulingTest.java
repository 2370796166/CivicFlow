package com.civicflow.queue;

import static org.assertj.core.api.Assertions.*;

import com.civicflow.queue.config.BackgroundSchedulingConfig;
import java.time.Instant;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class BackgroundSchedulingTest {
    @Test
    void blockedNotificationDoesNotDelayRecoveryTask() throws Exception {
        var config = new BackgroundSchedulingConfig();
        var recovery = config.recoveryScheduler(1);
        var notification = config.notificationScheduler();
        recovery.initialize();
        notification.initialize();
        var blocked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var recovered = new CountDownLatch(1);
        try {
            notification.schedule(
                    () -> {
                        blocked.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    },
                    Instant.now());
            assertThat(blocked.await(2, TimeUnit.SECONDS)).isTrue();
            recovery.schedule(recovered::countDown, Instant.now());
            assertThat(recovered.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            release.countDown();
            notification.shutdown();
            recovery.shutdown();
        }
    }
}
