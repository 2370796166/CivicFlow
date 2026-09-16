package com.civicflow.resource.controller;

import com.civicflow.common.api.ApiResponse;
import com.civicflow.common.api.PageResponse;
import com.civicflow.resource.config.RequestIdFilter;
import com.civicflow.resource.config.SecurityPrincipals;
import com.civicflow.resource.dto.request.AdjustSlotQuotaRequest;
import com.civicflow.resource.dto.request.BatchCreateSlotsRequest;
import com.civicflow.resource.dto.request.ChangeSlotStatusRequest;
import com.civicflow.resource.dto.request.CreateSlotRequest;
import com.civicflow.resource.dto.request.UpdateSlotRequest;
import com.civicflow.resource.dto.response.SlotBatchResponse;
import com.civicflow.resource.dto.response.SlotResponse;
import com.civicflow.resource.enums.SlotStatus;
import com.civicflow.resource.service.SlotAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
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
@RequestMapping("/api/v1/admin/slots")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSlotController {
    private final SlotAdminService service;

    public AdminSlotController(SlotAdminService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<PageResponse<SlotResponse>> list(
            @RequestParam(required = false) Long outletId,
            @RequestParam(required = false) Long itemId,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) SlotStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        return ApiResponse.success(
                service.list(outletId, itemId, dateFrom, dateTo, status, page, size),
                RequestIdFilter.current(request));
    }

    @GetMapping("/{id}")
    ApiResponse<SlotResponse> get(@PathVariable long id, HttpServletRequest request) {
        return ApiResponse.success(service.get(id), RequestIdFilter.current(request));
    }

    @PostMapping
    ApiResponse<SlotResponse> create(
            JwtAuthenticationToken authentication,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
            @Valid @RequestBody CreateSlotRequest body,
            HttpServletRequest request) {
        String requestId = RequestIdFilter.current(request);
        return ApiResponse.success(
                service.create(SecurityPrincipals.userId(authentication), key, requestId, body),
                requestId);
    }

    @PostMapping("/batch")
    ApiResponse<SlotBatchResponse> batchCreate(
            JwtAuthenticationToken authentication,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
            @Valid @RequestBody BatchCreateSlotsRequest body,
            HttpServletRequest request) {
        String requestId = RequestIdFilter.current(request);
        return ApiResponse.success(
                service.batchCreate(
                        SecurityPrincipals.userId(authentication), key, requestId, body),
                requestId);
    }

    @PutMapping("/{id}")
    ApiResponse<SlotResponse> update(
            JwtAuthenticationToken authentication,
            @PathVariable long id,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
            @Valid @RequestBody UpdateSlotRequest body,
            HttpServletRequest request) {
        String requestId = RequestIdFilter.current(request);
        return ApiResponse.success(
                service.update(SecurityPrincipals.userId(authentication), id, key, requestId, body),
                requestId);
    }

    @PatchMapping("/{id}/status")
    ApiResponse<SlotResponse> changeStatus(
            JwtAuthenticationToken authentication,
            @PathVariable long id,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
            @Valid @RequestBody ChangeSlotStatusRequest body,
            HttpServletRequest request) {
        String requestId = RequestIdFilter.current(request);
        return ApiResponse.success(
                service.changeStatus(
                        SecurityPrincipals.userId(authentication), id, key, requestId, body),
                requestId);
    }

    @PatchMapping("/{id}/quota")
    ApiResponse<SlotResponse> adjustQuota(
            JwtAuthenticationToken authentication,
            @PathVariable long id,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
            @Valid @RequestBody AdjustSlotQuotaRequest body,
            HttpServletRequest request) {
        String requestId = RequestIdFilter.current(request);
        return ApiResponse.success(
                service.adjustQuota(
                        SecurityPrincipals.userId(authentication), id, key, requestId, body),
                requestId);
    }
}
