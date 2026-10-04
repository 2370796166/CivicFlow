package com.civicflow.appointment.controller;

import com.civicflow.appointment.config.RequestIdFilter;
import com.civicflow.appointment.config.SecurityPrincipals;
import com.civicflow.appointment.service.StockReconciliationService;
import com.civicflow.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin/reconciliations")
public class AdminReconciliationController {
    private final StockReconciliationService service;

    public AdminReconciliationController(StockReconciliationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<StockReconciliationService.Report> reconcile(
            @Valid @RequestBody Request body,
            @RequestHeader("Idempotency-Key") String key,
            JwtAuthenticationToken authentication,
            HttpServletRequest request) {
        String requestId = RequestIdFilter.current(request);
        return ApiResponse.success(
                service.reconcileOne(
                        body.slotId(),
                        SecurityPrincipals.userId(authentication),
                        requestId,
                        body.repair(),
                        key),
                requestId);
    }

    public record Request(@Positive long slotId, boolean repair) {}
}
