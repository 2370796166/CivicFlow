package com.civicflow.resource.controller;

import com.civicflow.common.api.ApiResponse;
import com.civicflow.common.api.PageResponse;
import com.civicflow.resource.config.RequestIdFilter;
import com.civicflow.resource.config.SecurityPrincipals;
import com.civicflow.resource.dto.request.ChangeResourceStatusRequest;
import com.civicflow.resource.dto.request.CreateWindowRequest;
import com.civicflow.resource.dto.request.ReplaceWindowItemsRequest;
import com.civicflow.resource.dto.request.ReplaceWindowStaffRequest;
import com.civicflow.resource.dto.request.UpdateWindowRequest;
import com.civicflow.resource.dto.response.WindowItemsResponse;
import com.civicflow.resource.dto.response.WindowResponse;
import com.civicflow.resource.dto.response.WindowStaffResponse;
import com.civicflow.resource.enums.ResourceStatus;
import com.civicflow.resource.service.ResourceAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/admin/windows")
@PreAuthorize("hasRole('ADMIN')")
public class AdminWindowController {
    private final ResourceAdminService service;

    public AdminWindowController(ResourceAdminService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<PageResponse<WindowResponse>> list(
            @RequestParam(required = false) Long outletId,
            @RequestParam(required = false) @Size(max = 128) String keyword,
            @RequestParam(required = false) ResourceStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        return ApiResponse.success(
                service.listWindows(outletId, keyword, status, page, size),
                RequestIdFilter.current(request));
    }

    @GetMapping("/{id}")
    ApiResponse<WindowResponse> get(@PathVariable long id, HttpServletRequest request) {
        return ApiResponse.success(service.getWindow(id), RequestIdFilter.current(request));
    }

    @PostMapping
    ApiResponse<WindowResponse> create(
            JwtAuthenticationToken authentication,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
            @Valid @RequestBody CreateWindowRequest body,
            HttpServletRequest request) {
        String requestId = RequestIdFilter.current(request);
        return ApiResponse.success(
                service.createWindow(
                        SecurityPrincipals.userId(authentication), key, requestId, body),
                requestId);
    }

    @PutMapping("/{id}")
    ApiResponse<WindowResponse> update(
            JwtAuthenticationToken authentication,
            @PathVariable long id,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
            @Valid @RequestBody UpdateWindowRequest body,
            HttpServletRequest request) {
        String requestId = RequestIdFilter.current(request);
        return ApiResponse.success(
                service.updateWindow(
                        SecurityPrincipals.userId(authentication), id, key, requestId, body),
                requestId);
    }

    @PatchMapping("/{id}/status")
    ApiResponse<WindowResponse> changeStatus(
            JwtAuthenticationToken authentication,
            @PathVariable long id,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
            @Valid @RequestBody ChangeResourceStatusRequest body,
            HttpServletRequest request) {
        String requestId = RequestIdFilter.current(request);
        return ApiResponse.success(
                service.changeWindowStatus(
                        SecurityPrincipals.userId(authentication), id, key, requestId, body),
                requestId);
    }

    @GetMapping("/{id}/items")
    ApiResponse<WindowItemsResponse> getItems(@PathVariable long id, HttpServletRequest request) {
        return ApiResponse.success(service.getWindowItems(id), RequestIdFilter.current(request));
    }

    @GetMapping("/{id}/staff")
    ApiResponse<WindowStaffResponse> getStaff(@PathVariable long id, HttpServletRequest request) {
        return ApiResponse.success(service.getWindowStaff(id), RequestIdFilter.current(request));
    }

    @PutMapping("/{id}/staff")
    ApiResponse<WindowStaffResponse> replaceStaff(
            JwtAuthenticationToken authentication,
            @PathVariable long id,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
            @Valid @RequestBody ReplaceWindowStaffRequest body,
            HttpServletRequest request) {
        String requestId = RequestIdFilter.current(request);
        return ApiResponse.success(
                service.replaceWindowStaff(
                        SecurityPrincipals.userId(authentication), id, key, requestId, body),
                requestId);
    }

    @PutMapping("/{id}/items")
    ApiResponse<WindowItemsResponse> replaceItems(
            JwtAuthenticationToken authentication,
            @PathVariable long id,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
            @Valid @RequestBody ReplaceWindowItemsRequest body,
            HttpServletRequest request) {
        String requestId = RequestIdFilter.current(request);
        return ApiResponse.success(
                service.replaceWindowItems(
                        SecurityPrincipals.userId(authentication), id, key, requestId, body),
                requestId);
    }

    @DeleteMapping("/{id}")
    ApiResponse<Void> delete(
            JwtAuthenticationToken authentication,
            @PathVariable long id,
            @RequestParam @Min(0) int version,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
            HttpServletRequest request) {
        String requestId = RequestIdFilter.current(request);
        service.deleteWindow(
                SecurityPrincipals.userId(authentication), id, version, key, requestId);
        return ApiResponse.success(null, requestId);
    }
}
