package com.civicflow.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

class GatewayJwtValidatorTest {
    private final GatewayProperties.Jwt properties = new GatewayProperties.Jwt();
    private final OAuth2TokenValidator<Jwt> validator =
            GatewaySecurityConfig.tokenValidator(properties);

    @Test
    void rejectsExpiredToken() {
        Instant now = Instant.now();
        Jwt token = token(now.minusSeconds(180), now.minusSeconds(60), 1);

        assertThat(validator.validate(token).hasErrors()).isTrue();
    }

    @Test
    void rejectsMissingOrNegativeTokenVersion() {
        Instant now = Instant.now();
        Jwt token = token(now.minusSeconds(5), now.plusSeconds(60), -1);

        assertThat(validator.validate(token).hasErrors()).isTrue();
    }

    @Test
    void acceptsAuthTokenContract() {
        Instant now = Instant.now();
        Jwt token = token(now.minusSeconds(5), now.plusSeconds(60), 2);

        assertThat(validator.validate(token).hasErrors()).isFalse();
    }

    private static Jwt token(Instant issuedAt, Instant expiresAt, int tokenVersion) {
        return Jwt.withTokenValue("opaque-test-token")
                .header("alg", "RS256")
                .header("kid", "test-key")
                .issuer("https://auth.civicflow.local")
                .subject("1001")
                .audience(List.of("civicflow-api"))
                .issuedAt(issuedAt)
                .notBefore(issuedAt)
                .expiresAt(expiresAt)
                .claim("jti", "test-jti")
                .claim("roles", List.of("USER"))
                .claim("tokenVersion", tokenVersion)
                .claim("kid", "test-key")
                .build();
    }
}
