package com.civicflow.resource.controller;

import com.civicflow.common.api.ApiResponse;
import com.civicflow.common.api.PageResponse;
import com.civicflow.resource.config.RequestIdFilter;
import com.civicflow.resource.dto.response.UserSlotResponse;
import com.civicflow.resource.service.ResourceQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/user/slots")
@PreAuthorize("hasRole('USER')")
public class UserSlotController {
    private final ResourceQueryService service;

    public UserSlotController(ResourceQueryService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<PageResponse<UserSlotResponse>> list(
            @RequestParam @Min(1) long outletId,
            @RequestParam @Min(1) long itemId,
            @RequestParam LocalDate serviceDate,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        return ApiResponse.success(
                service.listAvailableSlots(outletId, itemId, serviceDate, page, size),
                RequestIdFilter.current(request));
    }
}
