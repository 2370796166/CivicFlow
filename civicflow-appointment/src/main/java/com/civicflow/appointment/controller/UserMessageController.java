package com.civicflow.appointment.controller;

import com.civicflow.appointment.config.*;
import com.civicflow.appointment.service.UserMessageService;
import com.civicflow.common.api.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/user/messages")
@PreAuthorize("hasRole('USER')")
public class UserMessageController {
    private final UserMessageService service;

    public UserMessageController(UserMessageService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<PageResponse<UserMessageService.Message>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            JwtAuthenticationToken auth,
            HttpServletRequest request) {
        return ApiResponse.success(
                service.list(SecurityPrincipals.userId(auth), page, size),
                RequestIdFilter.current(request));
    }

    @GetMapping("/unread-count")
    ApiResponse<Long> unread(JwtAuthenticationToken auth, HttpServletRequest request) {
        return ApiResponse.success(
                service.unread(SecurityPrincipals.userId(auth)), RequestIdFilter.current(request));
    }

    @PostMapping("/{id}/read")
    ApiResponse<Void> read(
            @PathVariable @Positive long id,
            JwtAuthenticationToken auth,
            HttpServletRequest request) {
        service.read(SecurityPrincipals.userId(auth), id);
        return ApiResponse.success(null, RequestIdFilter.current(request));
    }
}
