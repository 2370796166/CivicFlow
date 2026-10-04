package com.civicflow.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.civicflow.gateway.config.GatewayProperties;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SentinelGatewayRateLimiterTest {
    @Test
    void limitsRepeatedRequestsForTheSameHotKey() {
        GatewayProperties properties = new GatewayProperties();
        properties.getRateLimit().setLoginPermits(1);
        properties.getRateLimit().setLoginWindow(Duration.ofMinutes(1));
        properties.getRateLimit().setReservationPermits(1);
        properties.getRateLimit().setReservationWindow(Duration.ofMinutes(1));
        SentinelGatewayRateLimiter limiter = new SentinelGatewayRateLimiter(properties);
        limiter.configureRules();
        String key = UUID.randomUUID().toString();

        assertThat(limiter.tryAcquire(GatewayRateLimiter.LOGIN_RESOURCE, key)).isTrue();
        assertThat(limiter.tryAcquire(GatewayRateLimiter.LOGIN_RESOURCE, key)).isFalse();
    }
}
