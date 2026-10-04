package com.civicflow.resource.config;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class ResourceSecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            SecurityResponseWriter responseWriter)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(
                        sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        requests ->
                                requests.requestMatchers("/actuator/health", "/actuator/info")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth ->
                                oauth.jwt(
                                                jwt ->
                                                        jwt.decoder(jwtDecoder)
                                                                .jwtAuthenticationConverter(
                                                                        jwtAuthenticationConverter))
                                        .authenticationEntryPoint(responseWriter))
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(responseWriter)
                                        .accessDeniedHandler(responseWriter));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(ResourceSecurityProperties properties) {
        ResourceSecurityProperties.Jwt config = properties.getJwt();
        if (config.isAllowTestDecoder()) {
            return token -> {
                throw new JwtException("Test profile does not decode bearer tokens");
            };
        }
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withJwkSetUri(config.getJwkSetUri())
                        .jwsAlgorithm(SignatureAlgorithm.RS256)
                        .build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(config.getClockSkew());
        OAuth2TokenValidator<Jwt> issuerValidator =
                JwtValidators.createDefaultWithIssuer(config.getIssuer());
        OAuth2TokenValidator<Jwt> audienceValidator =
                jwt ->
                        jwt.getAudience().stream()
                                        .anyMatch(
                                                audience ->
                                                        audience.equals(config.getAudience())
                                                                || audience.equals(
                                                                        config
                                                                                .getServiceAudience()))
                                ? OAuth2TokenValidatorResult.success()
                                : OAuth2TokenValidatorResult.failure(
                                        new OAuth2Error(
                                                "invalid_token",
                                                "Required audience is missing",
                                                null));
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        timestampValidator, issuerValidator, audienceValidator));
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                jwt -> {
                    List<GrantedAuthority> authorities = new ArrayList<>();
                    Collection<String> roles = jwt.getClaimAsStringList("roles");
                    if (roles != null) {
                        roles.forEach(
                                role ->
                                        authorities.add(
                                                new SimpleGrantedAuthority("ROLE_" + role)));
                    }
                    Object scopeClaim = jwt.getClaim("scope");
                    if (scopeClaim instanceof String scopes) {
                        scopes.lines()
                                .flatMap(line -> List.of(line.split(" ")).stream())
                                .filter(scope -> !scope.isBlank())
                                .forEach(
                                        scope ->
                                                authorities.add(
                                                        new SimpleGrantedAuthority(
                                                                "SCOPE_" + scope)));
                    }
                    return authorities;
                });
        return converter;
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
