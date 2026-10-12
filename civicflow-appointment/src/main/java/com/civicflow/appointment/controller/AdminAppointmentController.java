package com.civicflow.appointment.controller;

import com.civicflow.appointment.config.RequestIdFilter;
import com.civicflow.appointment.dto.response.AdminAppointmentResponse;
import com.civicflow.appointment.enums.AppointmentStatus;
import com.civicflow.appointment.service.AdminAppointmentQueryService;
import com.civicflow.common.api.AdminQueryWindow;
import com.civicflow.common.api.ApiResponse;
import com.civicflow.common.api.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/admin/appointments")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAppointmentController {
    private final AdminAppointmentQueryService service;

    public AdminAppointmentController(AdminAppointmentQueryService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<PageResponse<AdminAppointmentResponse>> list(
            @RequestParam(required = false) @Size(max = 128) String keyword,
            @RequestParam(required = false) @Positive Long userId,
            @RequestParam(required = false) @Positive Long outletId,
            @RequestParam(required = false) @Positive Long itemId,
            @RequestParam(required = false) @Positive Long slotId,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate serviceDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return ApiResponse.success(
                service.list(
                        keyword,
                        userId,
                        outletId,
                        itemId,
                        slotId,
                        status,
                        new AdminQueryWindow(
                                page,
                                size,
                                serviceDate == null ? dateFrom : serviceDate,
                                serviceDate == null ? dateTo : serviceDate)),
                RequestIdFilter.current(request));
    }

    @GetMapping("/{id}")
    ApiResponse<AdminAppointmentResponse> get(
            @PathVariable @Positive long id, HttpServletRequest request) {
        return ApiResponse.success(service.get(id), RequestIdFilter.current(request));
    }
}
