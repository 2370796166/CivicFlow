package com.civicflow.appointment.controller;

import com.civicflow.appointment.config.RequestIdFilter;
import com.civicflow.appointment.dto.request.CheckInClaimRequest;
import com.civicflow.appointment.dto.request.QueueTicketLinkRequest;
import com.civicflow.appointment.dto.response.CheckInClaimResponse;
import com.civicflow.appointment.service.CheckInTokenService;
import com.civicflow.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
        "hasAuthority('SCOPE_appointments.checkin') and authentication.token.claims['serviceName'] == 'civicflow-queue'")
public class InternalCheckInController {
    private final CheckInTokenService service;

    public InternalCheckInController(CheckInTokenService service) {
        this.service = service;
    }

    @PostMapping("/check-in/claim")
    ApiResponse<CheckInClaimResponse> claim(
            @RequestBody @Valid CheckInClaimRequest body, HttpServletRequest request) {
        return ApiResponse.success(
                service.claim(
                        body.token(),
                        body.userId(),
                        body.outletId(),
                        body.claimId(),
                        RequestIdFilter.current(request)),
                RequestIdFilter.current(request));
    }

    @PostMapping("/{id}/queue-ticket-link")
    ApiResponse<Void> link(
            @PathVariable @Positive long id,
            @RequestBody @Valid QueueTicketLinkRequest body,
            HttpServletRequest request) {
        service.link(id, body.claimId(), body.ticketId());
        return ApiResponse.success(null, RequestIdFilter.current(request));
    }
}
