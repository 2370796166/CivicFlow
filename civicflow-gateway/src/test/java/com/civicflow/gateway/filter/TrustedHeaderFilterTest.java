package com.civicflow.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class TrustedHeaderFilterTest {
    private final RequestSanitizationWebFilter sanitizationFilter =
            new RequestSanitizationWebFilter();
    private final IdentityPropagationFilter identityFilter = new IdentityPropagationFilter();

    @Test
    void removesForgedHeadersBeforeInjectingVerifiedIdentity() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/test")
                        .header("X-User-Id", "attacker")
                        .header("X-User-Roles", "ADMIN")
                        .header("X-Service-Name", "forged-service")
                        .header("X-Request-Id", "forged-request")
                        .header("traceparent", "not-valid")
                        .build();
        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(jwt(), List.of(new SimpleGrantedAuthority("ROLE_USER")));
        ServerWebExchange exchange =
                MockServerWebExchange.from(request)
                        .mutate()
                        .principal(Mono.just(authentication))
                        .build();
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        sanitizationFilter
                .filter(
                        exchange,
                        sanitized ->
                                identityFilter.filter(
                                        sanitized,
                                        propagated -> {
                                            downstream.set(propagated);
                                            return Mono.empty();
                                        }))
                .block();

        HttpHeaders headers = downstream.get().getRequest().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isEqualTo("1001");
        assertThat(headers.getFirst("X-User-Roles")).isEqualTo("USER");
        assertThat(headers.getFirst("X-Service-Name")).isNull();
        assertThat(headers.getFirst("traceparent")).isNull();
        assertThat(headers.getFirst("X-Request-Id")).isNotBlank().isNotEqualTo("forged-request");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Request-Id"))
                .isEqualTo(headers.getFirst("X-Request-Id"));
    }

    private static Jwt jwt() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("1001")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("roles", List.of("USER"))
                .build();
    }
}
