package com.civicflow.auth.config;

import java.security.SecureRandom;
import java.time.Clock;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class AuthSecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            SecurityResponseWriter securityResponseWriter)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(
                        sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        requests ->
                                requests.requestMatchers(
                                                "/api/v1/auth/login",
                                                "/api/v1/auth/refresh",
                                                "/.well-known/jwks.json",
                                                "/actuator/health",
                                                "/actuator/info")
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
                                        .authenticationEntryPoint(securityResponseWriter))
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(securityResponseWriter)
                                        .accessDeniedHandler(securityResponseWriter));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(JwtKeyManager keyManager, AuthSecurityProperties properties) {
        return new KeyAwareJwtDecoder(keyManager, properties);
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                jwt -> {
                    Collection<String> roles = jwt.getClaimAsStringList("roles");
                    if (roles == null) {
                        return List.of();
                    }
                    return roles.stream()
                            .<GrantedAuthority>map(
                                    role -> new SimpleGrantedAuthority("ROLE_" + role))
                            .toList();
                });
        return converter;
    }

    @Bean
    PasswordEncoder passwordEncoder(AuthSecurityProperties properties) {
        return new BCryptPasswordEncoder(properties.getBcryptStrength());
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SecureRandom secureRandom() {
        return new SecureRandom();
    }
}
