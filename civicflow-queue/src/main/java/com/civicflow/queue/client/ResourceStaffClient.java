package com.civicflow.queue.client;

import com.civicflow.common.api.ApiResponse;
import com.civicflow.queue.config.QueueProperties;
import com.civicflow.queue.config.RequestIdFilter;
import feign.RequestInterceptor;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@FeignClient(
        name = "civicflow-resource",
        url = "${civicflow.queue.resource-url:}",
        configuration = ResourceStaffClient.Config.class)
public interface ResourceStaffClient {
    @GetMapping("/internal/v1/resource/staff/authorization")
    ApiResponse<Boolean> authorized(
            @RequestParam("staffUserId") long staffUserId, @RequestParam("outletId") long outletId);

    record Item(String id) {}

    record Scope(String outletId, String windowId, List<Item> items) {}

    @GetMapping("/internal/v1/resource/staff/{staffUserId}/scopes")
    ApiResponse<List<Scope>> scopes(@PathVariable("staffUserId") long staffUserId);

    class Config {
        @Bean
        RequestInterceptor resourceServiceInterceptor(QueueProperties properties) {
            return template -> {
                String token = properties.getResourceServiceToken();
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
}
