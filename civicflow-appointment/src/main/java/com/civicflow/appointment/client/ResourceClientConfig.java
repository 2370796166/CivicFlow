package com.civicflow.appointment.client;

import com.civicflow.appointment.config.AppointmentProperties;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

public class ResourceClientConfig {
    @Bean
    RequestInterceptor resourceServiceJwtInterceptor(AppointmentProperties properties) {
        return template -> {
            String token = properties.getResourceClient().getServiceToken();
            if (!StringUtils.hasText(token)) {
                throw new IllegalStateException("Resource service JWT is not configured");
            }
            template.header("Authorization", "Bearer " + token.trim());
        };
    }
}
