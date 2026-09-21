package com.civicflow.gateway.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("civicflow.gateway")
public class GatewayProperties {
    private Jwt jwt = new Jwt();
    private Cors cors = new Cors();
    private RateLimit rateLimit = new RateLimit();

    @Getter
    @Setter
    public static class Jwt {
        private String issuer = "https://auth.civicflow.local";
        private String audience = "civicflow-api";
        private String jwkSetUri = "http://civicflow-auth:8081/.well-known/jwks.json";
        private Duration clockSkew = Duration.ofSeconds(30);
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));
        private boolean allowCredentials = true;
    }

    @Getter
    @Setter
    public static class RateLimit {
        private int loginPermits = 10;
        private Duration loginWindow = Duration.ofMinutes(1);
        private int reservationPermits = 3;
        private Duration reservationWindow = Duration.ofSeconds(1);
        private int maxReservationBodyBytes = 4096;
    }
}
