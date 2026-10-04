package com.civicflow.appointment.controller;

import com.civicflow.appointment.config.RequestIdFilter;
import com.civicflow.appointment.service.QueueStateSyncService;
import com.civicflow.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/appointments")
@PreAuthorize(
        "hasAuthority('SCOPE_appointments.queue-state') and authentication.token.claims['serviceName'] == 'civicflow-queue'")
public class InternalQueueStateController {
    private final QueueStateSyncService service;

    public InternalQueueStateController(QueueStateSyncService service) {
        this.service = service;
    }

    public record Change(
            @Pattern(regexp = "[1-9][0-9]{0,18}") String ticketId,
            @NotBlank String status,
            @Pattern(regexp = "[1-9][0-9]{0,18}") String syncId) {}

    @PostMapping("/{id}/queue-state")
    ApiResponse<Void> change(
            @PathVariable @Positive long id,
            @RequestBody @Valid Change body,
            HttpServletRequest request) {
        service.apply(
                id,
                Long.parseLong(body.ticketId()),
                body.status(),
                body.syncId(),
                RequestIdFilter.current(request));
        return ApiResponse.success(null, RequestIdFilter.current(request));
    }
}
