package com.civicflow.queue.client;

import com.civicflow.queue.config.QueueProperties;
import com.civicflow.queue.config.RequestIdFilter;
import feign.RequestInterceptor;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class AppointmentClientConfig {
    @Bean
    RequestInterceptor appointmentServiceInterceptor(QueueProperties properties) {
        return template -> {
            String token = properties.getAppointmentServiceToken();
            if (!StringUtils.hasText(token)) {
                throw new IllegalStateException("Appointment service JWT is not configured");
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
