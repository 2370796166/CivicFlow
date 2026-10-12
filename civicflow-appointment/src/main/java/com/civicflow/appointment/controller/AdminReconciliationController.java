package com.civicflow.appointment.controller;

import com.civicflow.appointment.config.RequestIdFilter;
import com.civicflow.appointment.config.SecurityPrincipals;
import com.civicflow.appointment.enums.ReconciliationRunStatus;
import com.civicflow.appointment.service.AdminReconciliationQueryService;
import com.civicflow.appointment.service.StockReconciliationService;
import com.civicflow.common.api.AdminQueryWindow;
import com.civicflow.common.api.ApiResponse;
import com.civicflow.common.api.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin/reconciliations")
public class AdminReconciliationController {
    private final StockReconciliationService service;
    private final AdminReconciliationQueryService queries;

    public AdminReconciliationController(
            StockReconciliationService service, AdminReconciliationQueryService queries) {
        this.service = service;
        this.queries = queries;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<PageResponse<AdminReconciliationQueryService.History>> list(
            @RequestParam(required = false) @Positive Long slotId,
            @RequestParam(required = false) ReconciliationRunStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return ApiResponse.success(
                queries.list(slotId, status, new AdminQueryWindow(page, size, dateFrom, dateTo)),
                RequestIdFilter.current(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<AdminReconciliationQueryService.History> get(
            @PathVariable @Positive long id, HttpServletRequest request) {
        return ApiResponse.success(queries.get(id), RequestIdFilter.current(request));
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
