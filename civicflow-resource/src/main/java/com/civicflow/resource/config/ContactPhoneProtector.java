package com.civicflow.resource.config;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ContactPhoneProtector {
    private static final int NONCE_BYTES = 12;
    private final byte[] encryptionKey;
    private final byte[] idempotencyHmacKey;
    private final int keyVersion;
    private final SecureRandom secureRandom = new SecureRandom();

    public ContactPhoneProtector(ResourceSecurityProperties properties) {
        ResourceSecurityProperties.ContactProtection config = properties.getContactProtection();
        this.encryptionKey = loadKey(config.getEncryptionKey(), config.isAllowEphemeralTestKey());
        this.idempotencyHmacKey =
                loadKey(config.getIdempotencyHmacKey(), config.isAllowEphemeralTestKey());
        this.keyVersion = config.getKeyVersion();
    }

    public int keyVersion() {
        return keyVersion;
    }

    public String normalize(String value) {
        String normalized = value.replaceAll("[\\s()\u2010-\u2015-]", "");
        if (!normalized.matches("\\+?[0-9]{6,20}")) {
            throw new IllegalArgumentException("Invalid contact phone");
        }
        return normalized;
    }

    public byte[] encrypt(String normalized) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(encryptionKey, "AES"),
                    new GCMParameterSpec(128, nonce));
            cipher.updateAAD(ByteBuffer.allocate(4).putInt(keyVersion).array());
            byte[] encrypted = cipher.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(nonce.length + encrypted.length)
                    .put(nonce)
                    .put(encrypted)
                    .array();
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot encrypt contact phone", exception);
        }
    }

    public String decrypt(byte[] value, int storedKeyVersion) {
        if (storedKeyVersion != keyVersion) {
            throw new IllegalStateException("Unsupported contact phone key version");
        }
        try {
            byte[] nonce = Arrays.copyOfRange(value, 0, NONCE_BYTES);
            byte[] encrypted = Arrays.copyOfRange(value, NONCE_BYTES, value.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(encryptionKey, "AES"),
                    new GCMParameterSpec(128, nonce));
            cipher.updateAAD(ByteBuffer.allocate(4).putInt(storedKeyVersion).array());
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot decrypt contact phone", exception);
        }
    }

    public String mask(String normalized) {
        int visible = Math.min(4, normalized.length());
        return "*".repeat(normalized.length() - visible)
                + normalized.substring(normalized.length() - visible);
    }

    public byte[] payloadHash(String canonicalPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(idempotencyHmacKey, "HmacSHA256"));
            return mac.doFinal(
                    ("RESOURCE_IDEMPOTENCY\u0000" + canonicalPayload)
                            .getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot hash idempotency payload", exception);
        }
    }

    private static byte[] loadKey(String configured, boolean allowGenerated) {
        byte[] key;
        if (StringUtils.hasText(configured)) {
            key = Base64.getDecoder().decode(configured);
        } else if (allowGenerated) {
            key = new byte[32];
            new SecureRandom().nextBytes(key);
        } else {
            throw new IllegalStateException("Contact phone encryption key must be configured");
        }
        if (key.length != 32) {
            throw new IllegalStateException("Contact phone encryption key must be 256 bit");
        }
        return key;
    }
}
