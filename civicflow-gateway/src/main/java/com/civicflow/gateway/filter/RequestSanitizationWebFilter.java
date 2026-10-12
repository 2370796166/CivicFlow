package com.civicflow.gateway.filter;

import com.civicflow.common.trace.TraceHeaders;
import com.civicflow.gateway.support.GatewayRequestContext;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSanitizationWebFilter implements WebFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = UUID.randomUUID().toString();
        String traceparent = TraceHeaders.traceparent(requestId);
        exchange.getAttributes().put(GatewayRequestContext.REQUEST_ID_ATTRIBUTE, requestId);
        exchange.getResponse().getHeaders().set(GatewayRequestContext.REQUEST_ID_HEADER, requestId);

        ServerWebExchange sanitized =
                exchange.mutate()
                        .request(
                                request ->
                                        request.headers(
                                                headers ->
                                                        sanitize(headers, requestId, traceparent)))
                        .build();
        return chain.filter(sanitized);
    }

    private static void sanitize(
            HttpHeaders headers, String requestId, String generatedTraceparent) {
        for (String name : new ArrayList<>(headers.keySet())) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.startsWith("x-user-")
                    || lower.startsWith("x-service-")
                    || lower.startsWith("x-gateway-")
                    || lower.equals("x-request-id")) {
                headers.remove(name);
            }
        }
        headers.set("traceparent", generatedTraceparent);
        headers.set(GatewayRequestContext.REQUEST_ID_HEADER, requestId);
    }
}
