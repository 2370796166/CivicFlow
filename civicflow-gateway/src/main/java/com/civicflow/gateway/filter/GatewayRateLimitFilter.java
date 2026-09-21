package com.civicflow.gateway.filter;

import com.civicflow.gateway.config.GatewayProperties;
import com.civicflow.gateway.error.GatewayErrorCode;
import com.civicflow.gateway.ratelimit.GatewayRateLimiter;
import com.civicflow.gateway.support.GatewayResponseWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Optional;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class GatewayRateLimitFilter implements GlobalFilter, Ordered {
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String RESERVATION_PATH = "/api/v1/user/reservations";

    private final GatewayRateLimiter rateLimiter;
    private final GatewayResponseWriter responseWriter;
    private final GatewayProperties properties;
    private final ObjectMapper objectMapper;

    public GatewayRateLimitFilter(
            GatewayRateLimiter rateLimiter,
            GatewayResponseWriter responseWriter,
            GatewayProperties properties,
            ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.responseWriter = responseWriter;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (HttpMethod.POST.equals(exchange.getRequest().getMethod()) && LOGIN_PATH.equals(path)) {
            String clientIp = clientIp(exchange.getRequest().getRemoteAddress());
            return acquireOrReject(
                    exchange,
                    chain,
                    GatewayRateLimiter.LOGIN_RESOURCE,
                    clientIp,
                    properties.getRateLimit().getLoginWindow());
        }
        if (HttpMethod.POST.equals(exchange.getRequest().getMethod())
                && RESERVATION_PATH.equals(path)) {
            return rateLimitReservation(exchange, chain);
        }
        return chain.filter(exchange);
    }

    private Mono<Void> rateLimitReservation(ServerWebExchange exchange, GatewayFilterChain chain) {
        int maxBytes = properties.getRateLimit().getMaxReservationBodyBytes();
        return DataBufferUtils.join(exchange.getRequest().getBody(), maxBytes)
                .map(GatewayRateLimitFilter::readAndRelease)
                .defaultIfEmpty(new byte[0])
                .flatMap(
                        body -> {
                            ServerWebExchange replayable = withBody(exchange, body);
                            String slotId = slotId(body);
                            if (slotId == null) {
                                return chain.filter(replayable);
                            }
                            return replayable
                                    .getPrincipal()
                                    .ofType(JwtAuthenticationToken.class)
                                    .map(Optional::of)
                                    .defaultIfEmpty(Optional.empty())
                                    .flatMap(
                                            authentication -> {
                                                if (authentication.isEmpty()) {
                                                    return chain.filter(replayable);
                                                }
                                                return acquireOrReject(
                                                        replayable,
                                                        chain,
                                                        GatewayRateLimiter.RESERVATION_RESOURCE,
                                                        authentication.get().getName()
                                                                + ":"
                                                                + slotId,
                                                        properties
                                                                .getRateLimit()
                                                                .getReservationWindow());
                                            });
                        })
                .onErrorResume(
                        DataBufferLimitException.class,
                        exception ->
                                responseWriter.write(
                                        exchange,
                                        HttpStatus.BAD_REQUEST,
                                        GatewayErrorCode.BAD_REQUEST));
    }

    private static byte[] readAndRelease(DataBuffer buffer) {
        byte[] body = new byte[buffer.readableByteCount()];
        buffer.read(body);
        DataBufferUtils.release(buffer);
        return body;
    }

    private Mono<Void> acquireOrReject(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            String resource,
            String key,
            Duration window) {
        if (rateLimiter.tryAcquire(resource, key)) {
            return chain.filter(exchange);
        }
        exchange.getResponse()
                .getHeaders()
                .set("Retry-After", String.valueOf(Math.max(1, window.toSeconds())));
        return responseWriter.write(
                exchange, HttpStatus.TOO_MANY_REQUESTS, GatewayErrorCode.RATE_LIMITED);
    }

    private String slotId(byte[] body) {
        try {
            JsonNode node = objectMapper.readTree(body).get("slotId");
            if (node == null || (!node.isTextual() && !node.isIntegralNumber())) {
                return null;
            }
            String value = node.asText();
            return value.matches("[1-9][0-9]{0,18}") ? value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ServerWebExchange withBody(ServerWebExchange exchange, byte[] body) {
        ServerHttpRequestDecorator request =
                new ServerHttpRequestDecorator(exchange.getRequest()) {
                    @Override
                    public HttpHeaders getHeaders() {
                        HttpHeaders headers = new HttpHeaders();
                        headers.putAll(super.getHeaders());
                        headers.remove(HttpHeaders.TRANSFER_ENCODING);
                        headers.setContentLength(body.length);
                        return headers;
                    }

                    @Override
                    public Flux<DataBuffer> getBody() {
                        return Flux.defer(
                                () -> Flux.just(exchange.getResponse().bufferFactory().wrap(body)));
                    }
                };
        return exchange.mutate().request(request).build();
    }

    private static String clientIp(InetSocketAddress remoteAddress) {
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "unknown";
        }
        return remoteAddress.getAddress().getHostAddress();
    }

    @Override
    public int getOrder() {
        return -90;
    }
}
