package com.civicflow.appointment.controller;

import static com.civicflow.common.api.OperationLogResponse.text;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.civicflow.appointment.config.RequestIdFilter;
import com.civicflow.appointment.entity.AppointmentOperationLogEntity;
import com.civicflow.appointment.mapper.AppointmentOperationLogMapper;
import com.civicflow.common.api.AdminQueryWindow;
import com.civicflow.common.api.ApiResponse;
import com.civicflow.common.api.OperationLogResponse;
import com.civicflow.common.api.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin/operation-logs/appointment")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAppointmentLogController {
    private final AppointmentOperationLogMapper logs;

    public AdminAppointmentLogController(AppointmentOperationLogMapper logs) {
        this.logs = logs;
    }

    @GetMapping
    @Transactional(readOnly = true)
    ApiResponse<PageResponse<OperationLogResponse>> list(
            @RequestParam(required = false) @Positive Long resourceId,
            @RequestParam(required = false) @PositiveOrZero Long actorId,
            @RequestParam(required = false) @Size(max = 128) String requestId,
            @RequestParam(required = false) @Size(max = 64) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        var window = new AdminQueryWindow(page, size, dateFrom, dateTo);
        var query =
                Wrappers.<AppointmentOperationLogEntity>query()
                        .eq(resourceId != null, "appointment_id", resourceId)
                        .eq(actorId != null, "actor_id", actorId)
                        .eq(requestId != null && !requestId.isBlank(), "request_id", requestId)
                        .eq(action != null && !action.isBlank(), "operation", action)
                        .ge(dateFrom != null, "occurred_at", window.fromInstant())
                        .lt(dateTo != null, "occurred_at", window.toExclusiveInstant());
        long total = logs.selectCount(query);
        query.select(
                        "id",
                        "appointment_id",
                        "actor_type",
                        "actor_id",
                        "operation",
                        "from_status",
                        "to_status",
                        "request_id",
                        "occurred_at")
                .orderByDesc("occurred_at", "id")
                .last("LIMIT " + size + " OFFSET " + window.offset());
        var items =
                logs.selectList(query).stream()
                        .map(
                                row ->
                                        new OperationLogResponse(
                                                text(row.getId()),
                                                "appointment",
                                                "APPOINTMENT",
                                                text(row.getAppointmentId()),
                                                text(row.getActorType()),
                                                text(row.getActorId()),
                                                text(row.getOperation()),
                                                "SUCCESS",
                                                text(row.getFromStatus()),
                                                text(row.getToStatus()),
                                                row.getRequestId(),
                                                row.getOccurredAt()))
                        .toList();
        return ApiResponse.success(
                PageResponse.of(items, page, size, total), RequestIdFilter.current(request));
    }
}
