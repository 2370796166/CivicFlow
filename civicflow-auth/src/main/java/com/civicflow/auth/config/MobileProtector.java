package com.civicflow.auth.config;

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
public class MobileProtector {
    private static final int NONCE_BYTES = 12;
    private final byte[] encryptionKey;
    private final byte[] hmacKey;
    private final int keyVersion;
    private final SecureRandom secureRandom = new SecureRandom();

    public MobileProtector(AuthSecurityProperties properties) {
        AuthSecurityProperties.MobileProtection config = properties.getMobileProtection();
        this.encryptionKey =
                loadKey(config.getEncryptionKey(), 32, config.isAllowEphemeralTestKey());
        this.hmacKey = loadKey(config.getHmacKey(), 32, config.isAllowEphemeralTestKey());
        this.keyVersion = config.getKeyVersion();
    }

    public int keyVersion() {
        return keyVersion;
    }

    public String normalize(String mobile) {
        String normalized = mobile.replace(" ", "").replace("-", "");
        if (normalized.startsWith("+86")) {
            normalized = normalized.substring(3);
        } else if (normalized.startsWith("86") && normalized.length() == 13) {
            normalized = normalized.substring(2);
        }
        if (!normalized.matches("1[3-9]\\d{9}")) {
            throw new IllegalArgumentException("Invalid mobile number");
        }
        return normalized;
    }

    public byte[] hash(String normalizedMobile) {
        return hmacBytes(normalizedMobile);
    }

    public byte[] contextualHmac(String context, String value) {
        return hmacBytes(context + "\u0000" + value);
    }

    private byte[] hmacBytes(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot compute protected index", exception);
        }
    }

    public byte[] encrypt(String normalizedMobile) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(encryptionKey, "AES"),
                    new GCMParameterSpec(128, nonce));
            cipher.updateAAD(ByteBuffer.allocate(4).putInt(keyVersion).array());
            byte[] encrypted = cipher.doFinal(normalizedMobile.getBytes(StandardCharsets.UTF_8));
            ByteBuffer result = ByteBuffer.allocate(nonce.length + encrypted.length);
            return result.put(nonce).put(encrypted).array();
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot encrypt mobile", exception);
        }
    }

    public String decrypt(byte[] value, int storedKeyVersion) {
        if (storedKeyVersion != keyVersion) {
            throw new IllegalStateException("Unsupported mobile key version");
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
            throw new IllegalStateException("Cannot decrypt mobile", exception);
        }
    }

    public String mask(String normalizedMobile) {
        return normalizedMobile.substring(0, 3) + "****" + normalizedMobile.substring(7);
    }

    private static byte[] loadKey(String configured, int expectedBytes, boolean allowGenerated) {
        byte[] key;
        if (StringUtils.hasText(configured)) {
            key = Base64.getDecoder().decode(configured);
        } else if (allowGenerated) {
            key = new byte[expectedBytes];
            new SecureRandom().nextBytes(key);
        } else {
            throw new IllegalStateException("Mobile protection keys must be configured");
        }
        if (key.length != expectedBytes) {
            throw new IllegalStateException("Mobile protection keys must be 256 bit");
        }
        return key;
    }
}
