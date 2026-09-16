package com.civicflow.resource.controller;

import com.civicflow.common.api.ApiResponse;
import com.civicflow.common.api.PageResponse;
import com.civicflow.resource.config.RequestIdFilter;
import com.civicflow.resource.dto.response.ItemResponse;
import com.civicflow.resource.dto.response.OutletResponse;
import com.civicflow.resource.service.ResourceQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/user/outlets")
@PreAuthorize("hasRole('USER')")
public class UserResourceController {
    private final ResourceQueryService service;

    public UserResourceController(ResourceQueryService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<PageResponse<OutletResponse>> list(
            @RequestParam(required = false) @Size(max = 128) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        return ApiResponse.success(
                service.listAvailableOutlets(keyword, page, size),
                RequestIdFilter.current(request));
    }

    @GetMapping("/{outletId}")
    ApiResponse<OutletResponse> get(@PathVariable long outletId, HttpServletRequest request) {
        return ApiResponse.success(
                service.getAvailableOutlet(outletId), RequestIdFilter.current(request));
    }

    @GetMapping("/{outletId}/items")
    ApiResponse<PageResponse<ItemResponse>> listItems(
            @PathVariable long outletId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        return ApiResponse.success(
                service.listAvailableItems(outletId, page, size), RequestIdFilter.current(request));
    }
}
