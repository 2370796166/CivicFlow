package com.civicflow.queue.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("civicflow.queue")
public class QueueProperties {
    private Jwt jwt = new Jwt();
    private String appointmentServiceToken;
    private String resourceServiceToken;
    private boolean recoveryEnabled = true;

    @Getter
    @Setter
    public static class Jwt {
        private String issuer = "https://auth.civicflow.local";
        private String audience = "civicflow-api";
        private String jwkSetUri = "http://civicflow-auth:8081/.well-known/jwks.json";
        private Duration clockSkew = Duration.ofSeconds(30);
        private boolean allowTestDecoder;
    }
}
