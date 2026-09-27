package com.civicflow.resource.controller;

import com.civicflow.common.api.ApiResponse;
import com.civicflow.resource.config.RequestIdFilter;
import com.civicflow.resource.dto.response.SlotCandidatePageResponse;
import com.civicflow.resource.dto.response.SlotSnapshotResponse;
import com.civicflow.resource.service.ResourceSlotSnapshotService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/internal/v1/resource/slots")
@PreAuthorize(
        "hasAuthority('SCOPE_resource.slots.read') and authentication.token.claims['serviceName'] == 'civicflow-appointment'")
public class InternalResourceSlotController {
    private final ResourceSlotSnapshotService service;

    public InternalResourceSlotController(ResourceSlotSnapshotService service) {
        this.service = service;
    }

    @GetMapping("/{slotId}/snapshot")
    ApiResponse<SlotSnapshotResponse> get(
            @PathVariable @Positive long slotId, HttpServletRequest request) {
        return ApiResponse.success(service.get(slotId), RequestIdFilter.current(request));
    }

    @GetMapping("/preheat-candidates")
    ApiResponse<SlotCandidatePageResponse> candidates(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant releaseFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant releaseTo,
            @RequestParam(defaultValue = "0") @Min(0) long afterId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int size,
            HttpServletRequest request) {
        return ApiResponse.success(
                service.candidates(releaseFrom, releaseTo, afterId, size),
                RequestIdFilter.current(request));
    }

    @GetMapping("/reconciliation-candidates")
    ApiResponse<SlotCandidatePageResponse> reconciliationCandidates(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant at,
            @RequestParam(defaultValue = "0") @Min(0) long afterId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        return ApiResponse.success(
                service.reconciliationCandidates(at, afterId, size),
                RequestIdFilter.current(request));
    }
}
