package com.civicflow.appointment.controller;

import com.civicflow.appointment.config.RequestIdFilter;
import com.civicflow.appointment.config.SecurityPrincipals;
import com.civicflow.appointment.service.StockPreheatService;
import com.civicflow.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin/stock/slots")
public class AdminStockController {
    private final StockPreheatService service;

    public AdminStockController(StockPreheatService service) {
        this.service = service;
    }

    @PostMapping("/{slotId}/preheat")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<StockPreheatService.PreheatResult> preheat(
            @PathVariable @Positive long slotId,
            JwtAuthenticationToken authentication,
            HttpServletRequest request) {
        String requestId = RequestIdFilter.current(request);
        return ApiResponse.success(
                service.preheatOne(slotId, SecurityPrincipals.userId(authentication), requestId),
                requestId);
    }
}
