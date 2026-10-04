package com.civicflow.resource.controller;

import com.civicflow.common.api.ApiResponse;
import com.civicflow.resource.config.RequestIdFilter;
import com.civicflow.resource.dto.response.StaffScopeResponse;
import com.civicflow.resource.service.ResourceQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/resource")
@PreAuthorize(
        "hasAuthority('SCOPE_resource.windows.read') and authentication.token.claims['serviceName'] == 'civicflow-queue'")
public class InternalStaffAuthorizationController {
    private final ResourceQueryService service;

    public InternalStaffAuthorizationController(ResourceQueryService service) {
        this.service = service;
    }

    @GetMapping("/staff/authorization")
    ApiResponse<Boolean> authorized(
            @RequestParam @Positive long staffUserId,
            @RequestParam @Positive long outletId,
            HttpServletRequest request) {
        boolean allowed =
                service.listStaffScopes(staffUserId).stream()
                        .anyMatch(scope -> Long.toString(outletId).equals(scope.outletId()));
        return ApiResponse.success(allowed, RequestIdFilter.current(request));
    }

    @GetMapping("/staff/{staffUserId}/scopes")
    ApiResponse<List<StaffScopeResponse>> scopes(
            @PathVariable @Positive long staffUserId, HttpServletRequest request) {
        return ApiResponse.success(
                service.listStaffScopes(staffUserId), RequestIdFilter.current(request));
    }
}
