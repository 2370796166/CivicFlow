package com.civicflow.notification.client;

import com.civicflow.common.api.ApiResponse;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "civicflow-auth",
        contextId = "liveAdminAuth",
        url = "${civicflow.notification.auth-url:}")
public interface AuthCurrentUserClient {
    record User(String id, List<String> roles) {}

    @GetMapping("/api/v1/user/me")
    ApiResponse<User> currentUser(
            @RequestHeader("Authorization") String bearer,
            @RequestHeader("X-Request-Id") String requestId,
            @RequestHeader("traceparent") String traceparent);
}
