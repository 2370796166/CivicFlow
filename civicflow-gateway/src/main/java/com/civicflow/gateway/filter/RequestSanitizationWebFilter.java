package com.civicflow.gateway.filter;

import com.civicflow.gateway.support.GatewayRequestContext;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
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
    private static final Pattern TRACE_PARENT =
            Pattern.compile("^[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = UUID.randomUUID().toString();
        exchange.getAttributes().put(GatewayRequestContext.REQUEST_ID_ATTRIBUTE, requestId);
        exchange.getResponse().getHeaders().set(GatewayRequestContext.REQUEST_ID_HEADER, requestId);

        ServerWebExchange sanitized =
                exchange.mutate()
                        .request(
                                request -> request.headers(headers -> sanitize(headers, requestId)))
                        .build();
        return chain.filter(sanitized);
    }

    private static void sanitize(HttpHeaders headers, String requestId) {
        for (String name : new ArrayList<>(headers.keySet())) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.startsWith("x-user-")
                    || lower.startsWith("x-service-")
                    || lower.startsWith("x-gateway-")
                    || lower.equals("x-request-id")) {
                headers.remove(name);
            }
        }
        String traceparent = headers.getFirst("traceparent");
        if (traceparent != null && !TRACE_PARENT.matcher(traceparent).matches()) {
            headers.remove("traceparent");
        }
        headers.set(GatewayRequestContext.REQUEST_ID_HEADER, requestId);
    }
}
