package com.civicflow.gateway.support;

import java.util.UUID;
import org.springframework.web.server.ServerWebExchange;

public final class GatewayRequestContext {
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLES_HEADER = "X-User-Roles";
    public static final String REQUEST_ID_ATTRIBUTE =
            GatewayRequestContext.class.getName() + ".requestId";

    private GatewayRequestContext() {}

    public static String requestId(ServerWebExchange exchange) {
        Object value = exchange.getAttribute(REQUEST_ID_ATTRIBUTE);
        return value == null ? UUID.randomUUID().toString() : value.toString();
    }
}
