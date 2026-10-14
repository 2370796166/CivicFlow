package com.civicflow.notification.config;

import com.civicflow.notification.service.AdminAuthorizationService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class NotificationSecurityConfig {
    @Bean
    SecurityFilterChain security(
            HttpSecurity http,
            NotificationSecurityResponseWriter writer,
            AdminAuthorizationService adminAuthorization)
            throws Exception {
        return http.addFilterAfter(
                        new AdminAuthorizationFilter(adminAuthorization, writer),
                        BearerTokenAuthenticationFilter.class)
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        r ->
                                r.requestMatchers(
                                                "/actuator/health",
                                                "/actuator/health/**",
                                                "/actuator/info")
                                        .permitAll()
                                        .requestMatchers("/api/v1/admin/**")
                                        .hasRole("ADMIN")
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        o -> o.jwt(Customizer.withDefaults()).authenticationEntryPoint(writer))
                .exceptionHandling(
                        e -> e.authenticationEntryPoint(writer).accessDeniedHandler(writer))
                .build();
    }

    @Bean
    JwtDecoder decoder(
            @Value("${civicflow.notification.jwt.jwk-set-uri}") String jwks,
            @Value("${civicflow.notification.jwt.issuer}") String issuer,
            @Value("${civicflow.notification.jwt.audience}") String audience) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withJwkSetUri(jwks).jwsAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        new JwtTimestampValidator(Duration.ofSeconds(30)),
                        JwtValidators.createDefaultWithIssuer(issuer),
                        jwt ->
                                jwt.getAudience().contains(audience)
                                        ? OAuth2TokenValidatorResult.success()
                                        : OAuth2TokenValidatorResult.failure(
                                                new OAuth2Error("invalid_token"))));
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter converter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                jwt -> {
                    List<GrantedAuthority> result = new ArrayList<>();
                    List<String> roles = jwt.getClaimAsStringList("roles");
                    if (roles != null)
                        roles.forEach(
                                role -> result.add(new SimpleGrantedAuthority("ROLE_" + role)));
                    return result;
                });
        return converter;
    }
}
