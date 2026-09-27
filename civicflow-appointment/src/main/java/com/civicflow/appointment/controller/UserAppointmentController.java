package com.civicflow.appointment.controller;

import com.civicflow.appointment.config.RequestIdFilter;
import com.civicflow.appointment.config.SecurityPrincipals;
import com.civicflow.appointment.dto.request.CancelAppointmentRequest;
import com.civicflow.appointment.dto.request.ConfirmAppointmentRequest;
import com.civicflow.appointment.dto.request.CreateReservationRequest;
import com.civicflow.appointment.dto.response.AppointmentResponse;
import com.civicflow.appointment.dto.response.CheckInTokenResponse;
import com.civicflow.appointment.dto.response.ReservationCreateResponse;
import com.civicflow.appointment.dto.response.ReservationStatusResponse;
import com.civicflow.appointment.enums.AppointmentStatus;
import com.civicflow.appointment.error.AppointmentErrorCode;
import com.civicflow.appointment.service.AppointmentQueryService;
import com.civicflow.appointment.service.AppointmentStateService;
import com.civicflow.appointment.service.CheckInTokenService;
import com.civicflow.appointment.service.ReservationWorkflowService;
import com.civicflow.common.api.ApiResponse;
import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.api.GlobalErrorCode;
import com.civicflow.common.api.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping({"/api/user/appointments", "/api/v1/user/appointments"})
@PreAuthorize("hasRole('USER')")
public class UserAppointmentController {
    private final ReservationWorkflowService workflowService;
    private final AppointmentQueryService queryService;
    private final AppointmentStateService stateService;
    private final CheckInTokenService checkIn;

    public UserAppointmentController(
            ReservationWorkflowService workflowService,
            AppointmentQueryService queryService,
            AppointmentStateService stateService,
            CheckInTokenService checkIn) {
        this.workflowService = workflowService;
        this.queryService = queryService;
        this.stateService = stateService;
        this.checkIn = checkIn;
    }

    @PostMapping("/reservations")
    ResponseEntity<ApiResponse<ReservationCreateResponse>> reserve(
            @RequestBody @Valid CreateReservationRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            JwtAuthenticationToken authentication,
            HttpServletRequest request) {
        String requestId = RequestIdFilter.current(request);
        ReservationWorkflowService.CreateOutcome outcome =
                workflowService.create(
                        SecurityPrincipals.userId(authentication),
                        body.slotId(),
                        idempotencyKey,
                        requestId);
        if (outcome.dependencyUnavailable()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(
                            ApiResponse.failure(
                                    dependencyError(outcome.response().failureCode()),
                                    outcome.response(),
                                    requestId));
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(outcome.response(), requestId));
    }

    @GetMapping("/reservations/{reservationId}")
    ApiResponse<ReservationStatusResponse> reservation(
            @PathVariable @NotBlank String reservationId,
            JwtAuthenticationToken authentication,
            HttpServletRequest request) {
        return ApiResponse.success(
                queryService.getReservation(
                        SecurityPrincipals.userId(authentication), reservationId),
                RequestIdFilter.current(request));
    }

    @GetMapping
    ApiResponse<PageResponse<AppointmentResponse>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate serviceDate,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            JwtAuthenticationToken authentication,
            HttpServletRequest request) {
        return ApiResponse.success(
                queryService.listOwned(
                        SecurityPrincipals.userId(authentication), serviceDate, status, page, size),
                RequestIdFilter.current(request));
    }

    @GetMapping("/{appointmentId}")
    ApiResponse<AppointmentResponse> detail(
            @PathVariable @Positive long appointmentId,
            JwtAuthenticationToken authentication,
            HttpServletRequest request) {
        return ApiResponse.success(
                queryService.getOwned(SecurityPrincipals.userId(authentication), appointmentId),
                RequestIdFilter.current(request));
    }

    @PostMapping("/{appointmentId}/confirm")
    ApiResponse<AppointmentResponse> confirm(
            @PathVariable @Positive long appointmentId,
            @RequestBody @Valid ConfirmAppointmentRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            JwtAuthenticationToken authentication,
            HttpServletRequest request) {
        String traceId = RequestIdFilter.current(request);
        return ApiResponse.success(
                stateService.confirm(
                        SecurityPrincipals.userId(authentication),
                        appointmentId,
                        body.version(),
                        key,
                        traceId),
                traceId);
    }

    @PostMapping("/{appointmentId}/cancel")
    ApiResponse<AppointmentResponse> cancel(
            @PathVariable @Positive long appointmentId,
            @RequestBody @Valid CancelAppointmentRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            JwtAuthenticationToken authentication,
            HttpServletRequest request) {
        String traceId = RequestIdFilter.current(request);
        return ApiResponse.success(
                stateService.cancel(
                        SecurityPrincipals.userId(authentication),
                        appointmentId,
                        body.version(),
                        body.reason(),
                        key,
                        traceId),
                traceId);
    }

    @PostMapping("/{appointmentId}/check-in-token")
    ApiResponse<CheckInTokenResponse> checkInToken(
            @PathVariable @Positive long appointmentId,
            @RequestBody @Valid com.civicflow.appointment.dto.request.CheckInTokenRequest body,
            JwtAuthenticationToken authentication,
            HttpServletRequest request) {
        return ApiResponse.success(
                checkIn.issue(
                        SecurityPrincipals.userId(authentication), appointmentId, body.outletId()),
                RequestIdFilter.current(request));
    }

    private static GlobalErrorCode dependencyError(String failureCode) {
        if (AppointmentErrorCode.PUBLISH_FAILED.code().equals(failureCode)) {
            return AppointmentErrorCode.PUBLISH_FAILED;
        }
        if (AppointmentErrorCode.PUBLISH_UNKNOWN.code().equals(failureCode)) {
            return AppointmentErrorCode.PUBLISH_UNKNOWN;
        }
        if (AppointmentErrorCode.COMPENSATION_PENDING.code().equals(failureCode)) {
            return AppointmentErrorCode.COMPENSATION_PENDING;
        }
        return CommonErrorCode.DEPENDENCY_UNAVAILABLE;
    }
}
