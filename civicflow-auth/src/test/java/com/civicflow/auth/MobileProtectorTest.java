package com.civicflow.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.civicflow.auth.config.AuthSecurityProperties;
import com.civicflow.auth.config.MobileProtector;
import org.junit.jupiter.api.Test;

class MobileProtectorTest {
    @Test
    void normalizesHashesEncryptsAndMasksMobile() {
        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.getMobileProtection().setAllowEphemeralTestKey(true);
        MobileProtector protector = new MobileProtector(properties);

        String normalized = protector.normalize("+8613812345678");
        byte[] firstCipher = protector.encrypt(normalized);
        byte[] secondCipher = protector.encrypt(normalized);

        assertEquals("13812345678", normalized);
        assertArrayEquals(protector.hash(normalized), protector.hash(normalized));
        assertNotEquals(
                java.util.Arrays.toString(firstCipher), java.util.Arrays.toString(secondCipher));
        assertEquals(normalized, protector.decrypt(firstCipher, protector.keyVersion()));
        assertEquals("138****5678", protector.mask(normalized));
    }
}
