package com.civicflow.notification.controller;

import com.civicflow.common.api.ApiResponse;
import com.civicflow.common.api.PageResponse;
import com.civicflow.notification.config.NotificationRequestIdFilter;
import com.civicflow.notification.dto.NotificationFailureView;
import com.civicflow.notification.dto.NotificationView;
import com.civicflow.notification.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/user/notifications")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<PageResponse<NotificationView>> own(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            JwtAuthenticationToken auth) {
        return ApiResponse.success(service.own(userId(auth), page, size), requestId());
    }

    @PostMapping("/api/v1/user/notifications/{id}/read")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Map<String, Boolean>> read(
            @PathVariable @Positive long id, JwtAuthenticationToken auth) {
        service.read(userId(auth), id);
        return ApiResponse.success(Map.of("read", true), requestId());
    }

    @GetMapping("/api/v1/user/notification-preferences")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Map<String, Boolean>> preference(JwtAuthenticationToken auth) {
        return ApiResponse.success(
                Map.of("optionalInAppEnabled", service.preference(userId(auth))), requestId());
    }

    @PutMapping("/api/v1/user/notification-preferences")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Map<String, Boolean>> preference(
            @RequestBody @Valid PreferenceRequest body, JwtAuthenticationToken auth) {
        service.preference(userId(auth), body.optionalInAppEnabled());
        return ApiResponse.success(
                Map.of("optionalInAppEnabled", body.optionalInAppEnabled()), requestId());
    }

    @GetMapping("/api/v1/admin/notifications/failures")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<NotificationFailureView>> failures(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(service.failures(page, size), requestId());
    }

    @PostMapping("/api/v1/admin/notifications/{id}/replay")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Boolean>> replay(
            @PathVariable @Positive long id,
            @RequestBody @Valid ReplayRequest body,
            JwtAuthenticationToken auth) {
        service.replay(userId(auth), id, body.reason());
        return ApiResponse.success(Map.of("replayed", true), requestId());
    }

    private static long userId(JwtAuthenticationToken auth) {
        return Long.parseLong(auth.getToken().getSubject());
    }

    private static String requestId() {
        return NotificationRequestIdFilter.current();
    }

    public record PreferenceRequest(Boolean optionalInAppEnabled) {
        public PreferenceRequest {
            if (optionalInAppEnabled == null)
                throw new IllegalArgumentException("Preference is required");
        }
    }

    public record ReplayRequest(@NotBlank @Size(max = 256) String reason) {}
}
