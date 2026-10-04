package com.civicflow.gateway.config;

import com.civicflow.gateway.error.GatewayErrorCode;
import com.civicflow.gateway.support.GatewayResponseWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Flux;

@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewaySecurityConfig {
    private static final OAuth2Error INVALID_TOKEN =
            new OAuth2Error("invalid_token", "JWT claims are invalid", null);
    private static final List<String> ALLOWED_ROLES = List.of("USER", "STAFF", "ADMIN");

    @Bean
    SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ReactiveJwtDecoder jwtDecoder,
            ReactiveJwtAuthenticationConverter jwtAuthenticationConverter,
            ServerAuthenticationEntryPoint authenticationEntryPoint,
            ServerAccessDeniedHandler accessDeniedHandler) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(
                        exchanges ->
                                exchanges
                                        .pathMatchers(HttpMethod.OPTIONS, "/**")
                                        .permitAll()
                                        .pathMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/auth/login",
                                                "/api/v1/auth/refresh")
                                        .permitAll()
                                        .pathMatchers(HttpMethod.GET, "/actuator/health")
                                        .permitAll()
                                        .pathMatchers("/internal/**")
                                        .denyAll()
                                        .pathMatchers("/api/v1/admin/**")
                                        .hasRole("ADMIN")
                                        .pathMatchers("/api/v1/staff/**")
                                        .hasRole("STAFF")
                                        .pathMatchers("/api/v1/user/me")
                                        .hasAnyRole("USER", "STAFF", "ADMIN")
                                        .pathMatchers("/api/v1/user/**")
                                        .hasRole("USER")
                                        .pathMatchers("/api/v1/auth/logout")
                                        .authenticated()
                                        .anyExchange()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth ->
                                oauth.jwt(
                                                jwt ->
                                                        jwt.jwtDecoder(jwtDecoder)
                                                                .jwtAuthenticationConverter(
                                                                        jwtAuthenticationConverter))
                                        .authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(authenticationEntryPoint)
                                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    @Bean
    ReactiveJwtDecoder gatewayJwtDecoder(GatewayProperties properties) {
        GatewayProperties.Jwt config = properties.getJwt();
        NimbusReactiveJwtDecoder decoder =
                NimbusReactiveJwtDecoder.withJwkSetUri(config.getJwkSetUri())
                        .jwsAlgorithm(SignatureAlgorithm.RS256)
                        .build();
        decoder.setJwtValidator(tokenValidator(config));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> tokenValidator(GatewayProperties.Jwt config) {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(config.getClockSkew()),
                new JwtIssuerValidator(config.getIssuer()),
                jwt ->
                        jwt.getAudience().contains(config.getAudience())
                                ? OAuth2TokenValidatorResult.success()
                                : OAuth2TokenValidatorResult.failure(INVALID_TOKEN),
                jwt -> validateRequiredClaims(jwt, config.getClockSkew()));
    }

    private static OAuth2TokenValidatorResult validateRequiredClaims(Jwt jwt, Duration clockSkew) {
        String subject = jwt.getSubject();
        String jti = jwt.getId();
        String headerKid = (String) jwt.getHeaders().get("kid");
        String claimKid = jwt.getClaimAsString("kid");
        Number tokenVersion = jwt.getClaim("tokenVersion");
        Collection<String> roles = jwt.getClaimAsStringList("roles");
        boolean numericSubject = isPositiveLong(subject);
        boolean validRoles =
                roles != null
                        && !roles.isEmpty()
                        && roles.stream().allMatch(ALLOWED_ROLES::contains);
        boolean valid =
                numericSubject
                        && jti != null
                        && !jti.isBlank()
                        && jwt.getIssuedAt() != null
                        && !jwt.getIssuedAt().isAfter(Instant.now().plus(clockSkew))
                        && jwt.getNotBefore() != null
                        && headerKid != null
                        && headerKid.equals(claimKid)
                        && tokenVersion != null
                        && tokenVersion.longValue() >= 0
                        && tokenVersion.longValue() <= Integer.MAX_VALUE
                        && validRoles;
        return valid
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
    }

    private static boolean isPositiveLong(String value) {
        if (value == null || !value.matches("[1-9][0-9]*")) {
            return false;
        }
        try {
            return Long.parseLong(value) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    @Bean
    ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                jwt -> {
                    List<String> roles = jwt.getClaimAsStringList("roles");
                    if (roles == null) {
                        return Flux.empty();
                    }
                    return Flux.fromIterable(roles)
                            .map(
                                    role ->
                                            (GrantedAuthority)
                                                    new SimpleGrantedAuthority("ROLE_" + role));
                });
        return converter;
    }

    @Bean
    ServerAuthenticationEntryPoint authenticationEntryPoint(GatewayResponseWriter writer) {
        return (exchange, exception) ->
                writer.write(exchange, HttpStatus.UNAUTHORIZED, GatewayErrorCode.UNAUTHORIZED);
    }

    @Bean
    ServerAccessDeniedHandler accessDeniedHandler(GatewayResponseWriter writer) {
        return (exchange, exception) ->
                writer.write(exchange, HttpStatus.FORBIDDEN, GatewayErrorCode.FORBIDDEN);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(GatewayProperties properties) {
        GatewayProperties.Cors cors = properties.getCors();
        if (cors.isAllowCredentials() && cors.getAllowedOrigins().contains("*")) {
            throw new IllegalStateException("Credentialed CORS must use explicit allowed origins");
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(cors.getAllowedOrigins());
        configuration.setAllowCredentials(cors.isAllowCredentials());
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "Idempotency-Key", "Accept"));
        configuration.setExposedHeaders(List.of("X-Request-Id", "Retry-After"));
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
