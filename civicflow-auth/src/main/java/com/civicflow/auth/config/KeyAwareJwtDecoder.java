package com.civicflow.auth.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

public class KeyAwareJwtDecoder implements JwtDecoder {
    private static final OAuth2Error INVALID_TOKEN =
            new OAuth2Error("invalid_token", "JWT is invalid", null);

    private final Map<String, JwtDecoder> decoders;

    public KeyAwareJwtDecoder(JwtKeyManager keyManager, AuthSecurityProperties properties) {
        OAuth2TokenValidator<Jwt> validator =
                new DelegatingOAuth2TokenValidator<>(
                        new JwtTimestampValidator(properties.getJwt().getClockSkew()),
                        new JwtIssuerValidator(properties.getJwt().getIssuer()),
                        audience(properties.getJwt().getAccessAudience()));
        Map<String, JwtDecoder> configured = new HashMap<>();
        keyManager
                .verificationKeys()
                .forEach(
                        (kid, key) -> {
                            try {
                                NimbusJwtDecoder decoder =
                                        NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey())
                                                .signatureAlgorithm(SignatureAlgorithm.RS256)
                                                .build();
                                decoder.setJwtValidator(validator);
                                configured.put(kid, decoder);
                            } catch (JOSEException exception) {
                                throw new IllegalStateException(
                                        "Invalid RSA verification key", exception);
                            }
                        });
        this.decoders = Map.copyOf(configured);
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        try {
            SignedJWT parsed = SignedJWT.parse(token);
            String kid = parsed.getHeader().getKeyID();
            if (!JWSAlgorithm.RS256.equals(parsed.getHeader().getAlgorithm())
                    || kid == null
                    || !decoders.containsKey(kid)) {
                throw new JwtException("JWT algorithm or kid is not allowed");
            }
            return decoders.get(kid).decode(token);
        } catch (JwtException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new JwtException("JWT is malformed", exception);
        }
    }

    private static OAuth2TokenValidator<Jwt> audience(String expectedAudience) {
        return jwt -> {
            List<String> audience = jwt.getAudience();
            return audience.contains(expectedAudience)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        };
    }
}
