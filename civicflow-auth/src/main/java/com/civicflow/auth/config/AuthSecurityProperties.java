package com.civicflow.auth.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("civicflow.auth")
public class AuthSecurityProperties {
    private Jwt jwt = new Jwt();
    private MobileProtection mobileProtection = new MobileProtection();
    private int bcryptStrength = 12;

    @Getter
    @Setter
    public static class Jwt {
        private String issuer = "https://auth.civicflow.local";
        private String accessAudience = "civicflow-api";
        private Duration accessTokenTtl = Duration.ofMinutes(15);
        private Duration refreshTokenTtl = Duration.ofDays(7);
        private Duration clockSkew = Duration.ofSeconds(30);
        private String activeKeyId;
        private boolean allowEphemeralTestKey;
        private List<Key> keys = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Key {
        private String kid;
        private String publicKey;
        private String privateKey;
    }

    @Getter
    @Setter
    public static class MobileProtection {
        private int keyVersion = 1;
        private String encryptionKey;
        private String hmacKey;
        private boolean allowEphemeralTestKey;
    }
}
