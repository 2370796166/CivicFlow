package com.civicflow.appointment.controller;

import com.civicflow.appointment.config.RequestIdFilter;
import com.civicflow.appointment.dto.response.AdminAppointmentResponse;
import com.civicflow.appointment.dto.response.AppointmentLogResponse;
import com.civicflow.appointment.enums.AppointmentOperation;
import com.civicflow.appointment.enums.AppointmentStatus;
import com.civicflow.appointment.service.AdminAppointmentQueryService;
import com.civicflow.common.api.ApiResponse;
import com.civicflow.common.api.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class AdminAppointmentController {
    private final AdminAppointmentQueryService service;

    public AdminAppointmentController(AdminAppointmentQueryService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/admin/appointments")
    ApiResponse<PageResponse<AdminAppointmentResponse>> list(
            @RequestParam(required = false) @Min(1) Long userId,
            @RequestParam(required = false) @Min(1) Long outletId,
            @RequestParam(required = false) LocalDate serviceDate,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        return ApiResponse.success(
                service.list(userId, outletId, serviceDate, status, page, size),
                RequestIdFilter.current(request));
    }

    @GetMapping("/api/v1/admin/appointments/{id}")
    ApiResponse<AdminAppointmentResponse> get(
            @PathVariable @Min(1) long id, HttpServletRequest request) {
        return ApiResponse.success(service.get(id), RequestIdFilter.current(request));
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
