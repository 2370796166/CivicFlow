package com.civicflow.queue.controller;

import com.civicflow.common.api.ApiResponse;
import com.civicflow.queue.config.RequestIdFilter;
import com.civicflow.queue.dto.request.CheckInRequest;
import com.civicflow.queue.dto.response.QueueTicketResponse;
import com.civicflow.queue.service.CheckInService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1", "/api"})
public class QueueCheckInController {
    private final CheckInService service;

    public QueueCheckInController(CheckInService service) {
        this.service = service;
    }

    @PostMapping("/user/check-ins")
    @PreAuthorize("hasRole('USER')")
    ApiResponse<QueueTicketResponse> self(
            @RequestBody @Valid CheckInRequest body,
            JwtAuthenticationToken authentication,
            HttpServletRequest request) {
        return ApiResponse.success(
                service.selfCheckIn(
                        Long.parseLong(authentication.getToken().getSubject()),
                        body.outletId(),
                        body.checkInToken()),
                RequestIdFilter.current(request));
    }

    @PostMapping("/staff/check-ins")
    @PreAuthorize("hasRole('STAFF')")
    ApiResponse<QueueTicketResponse> staff(
            @RequestBody @Valid CheckInRequest body,
            JwtAuthenticationToken authentication,
            HttpServletRequest request) {
        return ApiResponse.success(
                service.staffCheckIn(
                        Long.parseLong(authentication.getToken().getSubject()),
                        body.outletId(),
                        body.checkInToken()),
                RequestIdFilter.current(request));
    }
}
