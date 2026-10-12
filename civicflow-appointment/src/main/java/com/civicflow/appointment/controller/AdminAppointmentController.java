package com.civicflow.appointment.controller;

import com.civicflow.appointment.config.RequestIdFilter;
import com.civicflow.appointment.dto.response.AppointmentResponse;
import com.civicflow.appointment.enums.AppointmentStatus;
import com.civicflow.appointment.service.AdminAppointmentQueryService;
import com.civicflow.common.api.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAppointmentController {
    private final AdminAppointmentQueryService queries;

    public AdminAppointmentController(AdminAppointmentQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/appointments")
    ApiResponse<PageResponse<AppointmentResponse>> list(
            @RequestParam(required = false) @Positive Long outletId,
            @RequestParam(required = false) @Positive Long itemId,
            @RequestParam(required = false) @Positive Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateTo,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        return ApiResponse.success(
                queries.appointments(
                        outletId, itemId, userId, dateFrom, dateTo, status, page, size),
                RequestIdFilter.current(request));
    }

    @GetMapping("/appointments/{id}")
    ApiResponse<AppointmentResponse> get(
            @PathVariable @Positive long id, HttpServletRequest request) {
        return ApiResponse.success(queries.get(id), RequestIdFilter.current(request));
    }

    @GetMapping("/operation-logs")
    ApiResponse<PageResponse<AdminAppointmentQueryService.Log>> logs(
            @RequestParam(required = false) @Positive Long appointmentId,
            @RequestParam(required = false) @Size(max = 64) String requestId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        return ApiResponse.success(
                queries.logs(appointmentId, requestId, page, size),
                RequestIdFilter.current(request));
    }

    @GetMapping("/reconciliations")
    ApiResponse<PageResponse<AdminAppointmentQueryService.Run>> runs(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        return ApiResponse.success(queries.runs(page, size), RequestIdFilter.current(request));
    }

    @GetMapping("/reconciliations/{id}")
    ApiResponse<PageResponse<AdminAppointmentQueryService.Detail>> details(
            @PathVariable @Positive long id,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        return ApiResponse.success(
                queries.details(id, page, size), RequestIdFilter.current(request));
    }

    @GetMapping("/appointments/statistics")
    ApiResponse<java.util.List<AdminAppointmentQueryService.Statistic>> statistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            HttpServletRequest request) {
        return ApiResponse.success(
                queries.statistics(dateFrom, dateTo), RequestIdFilter.current(request));
    }
}
