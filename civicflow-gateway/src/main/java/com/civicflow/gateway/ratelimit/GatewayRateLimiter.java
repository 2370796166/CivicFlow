package com.civicflow.gateway.ratelimit;

public interface GatewayRateLimiter {
    String LOGIN_RESOURCE = "civicflow-gateway-login-by-ip";
    String RESERVATION_RESOURCE = "civicflow-gateway-reservation-by-user-slot";

    boolean tryAcquire(String resource, String key);
}
