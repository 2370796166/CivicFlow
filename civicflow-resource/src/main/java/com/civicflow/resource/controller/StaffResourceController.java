package com.civicflow.resource.controller;

import com.civicflow.common.api.ApiResponse;
import com.civicflow.resource.config.RequestIdFilter;
import com.civicflow.resource.config.SecurityPrincipals;
import com.civicflow.resource.dto.response.StaffScopeResponse;
import com.civicflow.resource.service.ResourceQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/staff/scopes")
@PreAuthorize("hasRole('STAFF')")
public class StaffResourceController {
    private final ResourceQueryService service;

    public StaffResourceController(ResourceQueryService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<List<StaffScopeResponse>> list(
            JwtAuthenticationToken authentication, HttpServletRequest request) {
        return ApiResponse.success(
                service.listStaffScopes(SecurityPrincipals.userId(authentication)),
                RequestIdFilter.current(request));
    }
}
