package com.civicflow.appointment.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties("civicflow.appointment")
public class AppointmentProperties {
    @NotBlank private String environment = "dev";
    @Valid private Jwt jwt = new Jwt();
    @Valid private ResourceClient resourceClient = new ResourceClient();
    @Valid private Stock stock = new Stock();
    @Valid private Reservation reservation = new Reservation();
    @Valid private Recovery recovery = new Recovery();
    @Valid private Preheat preheat = new Preheat();
    @Valid private Reconciliation reconciliation = new Reconciliation();
    @Valid private CheckIn checkIn = new CheckIn();

    @Getter
    @Setter
    public static class CheckIn {
        private String signingKeyBase64;
        @NotBlank private String keyId = "checkin-v1";
        @NotNull private Duration tokenTtl = Duration.ofMinutes(2);
        private boolean allowTestKey;
    }

    @Getter
    @Setter
    public static class Jwt {
        @NotBlank private String issuer = "https://auth.civicflow.local";
        @NotBlank private String audience = "civicflow-api";
        @NotBlank private String jwkSetUri = "http://civicflow-auth:8081/.well-known/jwks.json";
        @NotNull private Duration clockSkew = Duration.ofSeconds(30);
        private boolean allowTestDecoder;
    }

    @Getter
    @Setter
    public static class ResourceClient {
        private String serviceToken;
    }

    @Getter
    @Setter
    public static class Stock {
        @NotNull private Duration reservationTtl = Duration.ofMinutes(10);
        @NotNull private Duration retentionAfterClose = Duration.ofDays(2);
    }

    @Getter
    @Setter
    public static class Reservation {
        @NotNull private Duration confirmDeadline = Duration.ofMinutes(5);
        @NotNull private Duration publishConfirmTimeout = Duration.ofSeconds(3);

        @Min(1)
        @Max(10)
        private int publishMaxAttempts = 3;

        @Min(1)
        @Max(10)
        private int consumerMaxAttempts = 3;

        @Min(100)
        @Max(10000)
        private int pollAfterMs = 500;
    }

    @Getter
    @Setter
    public static class Recovery {
        private boolean enabled = true;
        @NotNull private Duration fixedDelay = Duration.ofSeconds(10);
        @NotNull private Duration retryDelay = Duration.ofSeconds(30);
        @NotNull private Duration lease = Duration.ofSeconds(30);

        @Min(1)
        @Max(500)
        private int batchSize = 50;
    }

    @Getter
    @Setter
    public static class Preheat {
        private boolean enabled = true;
        @NotNull private Duration fixedDelay = Duration.ofMinutes(1);
        @NotNull private Duration futureWindow = Duration.ofHours(24);

        @Min(1)
        @Max(500)
        private int pageSize = 100;
    }

    @Getter
    @Setter
    public static class Reconciliation {
        private boolean enabled = true;
        @NotNull private Duration fixedDelay = Duration.ofMinutes(5);
        @NotNull private Duration gracePeriod = Duration.ofMinutes(2);
        @NotNull private Duration slotDelay = Duration.ofMillis(50);

        @Min(1)
        @Max(100)
        private int pageSize = 50;

        @Min(1)
        @Max(1000)
        private int maxSlotsPerRun = 500;
    }
}
