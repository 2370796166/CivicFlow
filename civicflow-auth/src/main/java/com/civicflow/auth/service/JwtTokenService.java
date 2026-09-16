package com.civicflow.auth.service;

import com.civicflow.auth.config.AuthSecurityProperties;
import com.civicflow.auth.config.JwtKeyManager;
import com.civicflow.auth.entity.SysUserEntity;
import com.civicflow.auth.enums.RoleCode;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private final AuthSecurityProperties properties;
    private final JwtKeyManager keyManager;
    private final JwtEncoder encoder;
    private final Clock clock;

    public JwtTokenService(
            AuthSecurityProperties properties, JwtKeyManager keyManager, Clock clock) {
        this.properties = properties;
        this.keyManager = keyManager;
        this.clock = clock;
        this.encoder =
                new NimbusJwtEncoder(
                        new ImmutableJWKSet<SecurityContext>(
                                new JWKSet(keyManager.activeSigningKey())));
    }

    public AccessToken issue(SysUserEntity user, List<RoleCode> roles) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.getJwt().getAccessTokenTtl());
        List<String> roleNames = roles.stream().map(RoleCode::name).sorted().toList();
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(properties.getJwt().getIssuer())
                        .subject(String.valueOf(user.getId()))
                        .audience(List.of(properties.getJwt().getAccessAudience()))
                        .issuedAt(issuedAt)
                        .notBefore(issuedAt)
                        .expiresAt(expiresAt)
                        .id(UUID.randomUUID().toString())
                        .claim("roles", roleNames)
                        .claim("tokenVersion", user.getTokenVersion())
                        .claim("kid", keyManager.activeKeyId())
                        .build();
        JwsHeader header =
                JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyManager.activeKeyId()).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(
                value, properties.getJwt().getAccessTokenTtl().toSeconds(), expiresAt);
    }

    public record AccessToken(String value, long expiresIn, Instant expiresAt) {}
}
