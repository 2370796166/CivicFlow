package com.civicflow.resource.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("civicflow.resource")
public class ResourceSecurityProperties {
    private Jwt jwt = new Jwt();
    private ContactProtection contactProtection = new ContactProtection();

    @Getter
    @Setter
    public static class Jwt {
        private String issuer = "https://auth.civicflow.local";
        private String audience = "civicflow-api";
        private String serviceAudience = "civicflow-resource";
        private String jwkSetUri = "http://civicflow-auth:8081/.well-known/jwks.json";
        private Duration clockSkew = Duration.ofSeconds(30);
        private boolean allowTestDecoder;
    }

    @Getter
    @Setter
    public static class ContactProtection {
        private int keyVersion = 1;
        private String encryptionKey;
        private String idempotencyHmacKey;
        private boolean allowEphemeralTestKey;
    }
}
