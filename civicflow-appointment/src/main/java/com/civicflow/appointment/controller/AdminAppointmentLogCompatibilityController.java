package com.civicflow.appointment.controller;

import com.civicflow.appointment.config.RequestIdFilter;
import com.civicflow.appointment.dto.response.AppointmentLogResponse;
import com.civicflow.appointment.enums.AppointmentOperation;
import com.civicflow.appointment.service.AdminAppointmentQueryService;
import com.civicflow.common.api.ApiResponse;
import com.civicflow.common.api.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminAppointmentLogCompatibilityController {
    private final AdminAppointmentQueryService service;

    public AdminAppointmentLogCompatibilityController(AdminAppointmentQueryService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/admin/appointment-operation-logs")
    ApiResponse<PageResponse<AppointmentLogResponse>> logs(
            @RequestParam(required = false) @Min(1) Long appointmentId,
            @RequestParam(required = false) AppointmentOperation operation,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        return ApiResponse.success(
                service.logs(appointmentId, operation, page, size),
                RequestIdFilter.current(request));
    }
}
