package com.civicflow.auth.controller;

import com.civicflow.auth.config.RequestIdFilter;
import com.civicflow.auth.dto.request.ChangeUserStatusRequest;
import com.civicflow.auth.dto.request.CreateUserRequest;
import com.civicflow.auth.dto.request.ReplaceUserRolesRequest;
import com.civicflow.auth.dto.response.UserResponse;
import com.civicflow.auth.enums.UserStatus;
import com.civicflow.auth.service.UserAdminService;
import com.civicflow.common.api.ApiResponse;
import com.civicflow.common.api.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final UserAdminService userAdminService;

    public AdminUserController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<PageResponse<UserResponse>> list(
            JwtAuthenticationToken authentication,
            @RequestParam(required = false) @Size(max = 64) String keyword,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        return ApiResponse.success(
                userAdminService.listUsers(
                        actorId(authentication),
                        tokenVersion(authentication),
                        keyword,
                        status,
                        page,
                        size),
                RequestIdFilter.current(request));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<UserResponse> create(
            JwtAuthenticationToken authentication,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody CreateUserRequest body,
            HttpServletRequest request) {
        return ApiResponse.success(
                userAdminService.createUser(
                        actorId(authentication),
                        tokenVersion(authentication),
                        idempotencyKey,
                        RequestIdFilter.current(request),
                        body),
                RequestIdFilter.current(request));
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<UserResponse> changeStatus(
            JwtAuthenticationToken authentication,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @PathVariable long userId,
            @Valid @RequestBody ChangeUserStatusRequest body,
            HttpServletRequest request) {
        return ApiResponse.success(
                userAdminService.changeStatus(
                        actorId(authentication),
                        tokenVersion(authentication),
                        idempotencyKey,
                        RequestIdFilter.current(request),
                        userId,
                        body),
                RequestIdFilter.current(request));
    }

    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<UserResponse> replaceRoles(
            JwtAuthenticationToken authentication,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @PathVariable long userId,
            @Valid @RequestBody ReplaceUserRolesRequest body,
            HttpServletRequest request) {
        return ApiResponse.success(
                userAdminService.replaceRoles(
                        actorId(authentication),
                        tokenVersion(authentication),
                        idempotencyKey,
                        RequestIdFilter.current(request),
                        userId,
                        body),
                RequestIdFilter.current(request));
    }

    private static long actorId(JwtAuthenticationToken authentication) {
        return Long.parseLong(authentication.getName());
    }

    private static int tokenVersion(JwtAuthenticationToken authentication) {
        Number value = authentication.getToken().getClaim("tokenVersion");
        return value.intValue();
    }
}
