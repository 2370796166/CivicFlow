package com.civicflow.appointment.client;

import com.civicflow.appointment.config.AppointmentProperties;
import com.civicflow.appointment.config.RequestIdFilter;
import feign.RequestInterceptor;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class ResourceClientConfig {
    @Bean
    RequestInterceptor resourceServiceJwtInterceptor(AppointmentProperties properties) {
        return template -> {
            String token = properties.getResourceClient().getServiceToken();
            if (!StringUtils.hasText(token)) {
                throw new IllegalStateException("Resource service JWT is not configured");
            }
            template.header("Authorization", "Bearer " + token.trim());
            var context = RequestContextHolder.getRequestAttributes();
            String traceId =
                    context instanceof ServletRequestAttributes servlet
                            ? RequestIdFilter.current(servlet.getRequest())
                            : UUID.randomUUID().toString();
            template.header(RequestIdFilter.HEADER, traceId);
        };
    }
}
