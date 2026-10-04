package com.civicflow.auth.config;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class JwtKeyManager {
    private final String activeKeyId;
    private final Map<String, RSAKey> keys;

    public JwtKeyManager(AuthSecurityProperties properties) {
        AuthSecurityProperties.Jwt jwt = properties.getJwt();
        this.activeKeyId = jwt.getActiveKeyId();
        this.keys = loadKeys(jwt);
        RSAKey active = keys.get(activeKeyId);
        if (active == null || !active.isPrivate()) {
            throw new IllegalStateException(
                    "Active JWT kid must identify a configured private RSA key");
        }
    }

    public String activeKeyId() {
        return activeKeyId;
    }

    public RSAKey activeSigningKey() {
        return keys.get(activeKeyId);
    }

    public Map<String, RSAKey> verificationKeys() {
        return Map.copyOf(keys);
    }

    public Map<String, Object> publicJwkSet() {
        return new JWKSet(keys.values().stream().<JWK>map(RSAKey::toPublicJWK).toList())
                .toJSONObject();
    }

    private static Map<String, RSAKey> loadKeys(AuthSecurityProperties.Jwt properties) {
        Map<String, RSAKey> loaded = new LinkedHashMap<>();
        for (AuthSecurityProperties.Key configured : properties.getKeys()) {
            if (!StringUtils.hasText(configured.getKid())
                    || !StringUtils.hasText(configured.getPublicKey())) {
                continue;
            }
            RSAPublicKey publicKey = parsePublicKey(configured.getPublicKey());
            RSAKey.Builder builder = new RSAKey.Builder(publicKey).keyID(configured.getKid());
            if (StringUtils.hasText(configured.getPrivateKey())) {
                builder.privateKey(parsePrivateKey(configured.getPrivateKey()));
            }
            loaded.put(configured.getKid(), builder.build());
        }
        if (loaded.isEmpty() && properties.isAllowEphemeralTestKey()) {
            loaded.put(properties.getActiveKeyId(), generate(properties.getActiveKeyId()));
        }
        return loaded;
    }

    private static RSAKey generate(String kid) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID(kid)
                    .build();
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot generate test RSA key", exception);
        }
    }

    private static RSAPublicKey parsePublicKey(String encoded) {
        try {
            byte[] der = Base64.getDecoder().decode(stripPem(encoded));
            return (RSAPublicKey)
                    KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid JWT public key", exception);
        }
    }

    private static RSAPrivateKey parsePrivateKey(String encoded) {
        try {
            byte[] der = Base64.getDecoder().decode(stripPem(encoded));
            return (RSAPrivateKey)
                    KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid JWT private key", exception);
        }
    }

    private static String stripPem(String value) {
        return value.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
    }
}
