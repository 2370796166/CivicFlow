package com.civicflow.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.civicflow.gateway.config.GatewayProperties;
import com.civicflow.gateway.ratelimit.GatewayRateLimiter;
import com.civicflow.gateway.support.GatewayResponseWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class GatewayRateLimitFilterTest {
    @Test
    void writesUnified429WithRetryAfter() {
        RecordingRateLimiter limiter = new RecordingRateLimiter(false);
        GatewayRateLimitFilter filter = filter(limiter);
        MockServerWebExchange exchange =
                MockServerWebExchange.from(
                        MockServerHttpRequest.method(HttpMethod.POST, "/api/v1/auth/login")
                                .build());

        filter.filter(exchange, ignored -> Mono.error(new AssertionError("must not route")))
                .block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("60");
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("GATEWAY_429_RATE_LIMITED");
    }

    @Test
    void usesVerifiedUserAndSlotAsReservationHotKeyAndReplaysBody() {
        RecordingRateLimiter limiter = new RecordingRateLimiter(true);
        GatewayRateLimitFilter filter = filter(limiter);
        String requestBody = "{\"slotId\":\"42\"}";
        ServerWebExchange exchange =
                MockServerWebExchange.from(
                                MockServerHttpRequest.post("/api/v1/user/reservations")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(requestBody))
                        .mutate()
                        .principal(Mono.just(authentication()))
                        .build();
        AtomicReference<String> downstreamBody = new AtomicReference<>();

        filter.filter(
                        exchange,
                        routed ->
                                DataBufferUtils.join(routed.getRequest().getBody())
                                        .doOnNext(
                                                data -> {
                                                    byte[] bytes =
                                                            new byte[data.readableByteCount()];
                                                    data.read(bytes);
                                                    DataBufferUtils.release(data);
                                                    downstreamBody.set(new String(bytes));
                                                })
                                        .then())
                .block();

        assertThat(limiter.resource).isEqualTo(GatewayRateLimiter.RESERVATION_RESOURCE);
        assertThat(limiter.key).isEqualTo("1001:42");
        assertThat(downstreamBody.get()).isEqualTo(requestBody);
    }

    @Test
    void emptyReservationBodyStillRoutesForDownstreamValidation() {
        RecordingRateLimiter limiter = new RecordingRateLimiter(true);
        GatewayRateLimitFilter filter = filter(limiter);
        ServerWebExchange exchange =
                MockServerWebExchange.from(
                                MockServerHttpRequest.post("/api/v1/user/reservations").build())
                        .mutate()
                        .principal(Mono.just(authentication()))
                        .build();
        AtomicReference<Boolean> routed = new AtomicReference<>(false);

        filter.filter(
                        exchange,
                        ignored -> {
                            routed.set(true);
                            return Mono.empty();
                        })
                .block();

        assertThat(routed.get()).isTrue();
        assertThat(limiter.resource).isNull();
    }

    private static GatewayRateLimitFilter filter(GatewayRateLimiter limiter) {
        GatewayProperties properties = new GatewayProperties();
        properties.getRateLimit().setLoginWindow(Duration.ofMinutes(1));
        return new GatewayRateLimitFilter(
                limiter,
                new GatewayResponseWriter(new ObjectMapper()),
                properties,
                new ObjectMapper());
    }

    private static JwtAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt =
                Jwt.withTokenValue("test-token")
                        .header("alg", "RS256")
                        .subject("1001")
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(60))
                        .claim("roles", List.of("USER"))
                        .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private static final class RecordingRateLimiter implements GatewayRateLimiter {
        private final boolean allowed;
        private String resource;
        private String key;

        private RecordingRateLimiter(boolean allowed) {
            this.allowed = allowed;
        }

        @Override
        public boolean tryAcquire(String resource, String key) {
            this.resource = resource;
            this.key = key;
            return allowed;
        }
    }
}
