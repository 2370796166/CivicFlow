package com.civicflow.gateway.filter;

import com.civicflow.gateway.support.GatewayRequestContext;
import java.util.Comparator;
import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class IdentityPropagationFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .ofType(JwtAuthenticationToken.class)
                .map(authentication -> withIdentity(exchange, authentication))
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }

    private static ServerWebExchange withIdentity(
            ServerWebExchange exchange, JwtAuthenticationToken authentication) {
        List<String> roles = authentication.getToken().getClaimAsStringList("roles");
        String roleHeader =
                roles == null
                        ? ""
                        : roles.stream()
                                .sorted(Comparator.naturalOrder())
                                .distinct()
                                .reduce((left, right) -> left + "," + right)
                                .orElse("");
        return exchange.mutate()
                .request(
                        request ->
                                request.headers(
                                        headers -> {
                                            headers.set(
                                                    GatewayRequestContext.USER_ID_HEADER,
                                                    authentication.getToken().getSubject());
                                            headers.set(
                                                    GatewayRequestContext.USER_ROLES_HEADER,
                                                    roleHeader);
                                        }))
                .build();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
