package com.civicflow.queue.controller;

import com.civicflow.common.api.ApiResponse;
import com.civicflow.queue.config.RequestIdFilter;
import com.civicflow.queue.service.QueueWorkbenchService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1", "/api"})
public class QueueWorkbenchController {
    private final QueueWorkbenchService service;

    public QueueWorkbenchController(QueueWorkbenchService service) {
        this.service = service;
    }

    public record StartRequest(@Positive long windowId) {}

    public record VersionRequest(
            @Positive long sessionId,
            @NotNull @PositiveOrZero Integer version,
            @Size(max = 64) String resultCode) {}

    public record EndRequest(@NotNull @PositiveOrZero Integer version) {}

    public record CallNextRequest(java.util.List<Long> itemIds) {}

    private static long actor(JwtAuthenticationToken auth) {
        return Long.parseLong(auth.getToken().getSubject());
    }

    @GetMapping("/user/queue-tickets/current")
    @PreAuthorize("hasRole('USER')")
    ApiResponse<?> own(
            @RequestParam(required = false) Long appointmentId,
            JwtAuthenticationToken auth,
            HttpServletRequest request) {
        return ApiResponse.success(
                service.own(actor(auth), appointmentId), RequestIdFilter.current(request));
    }

    @GetMapping("/user/queue-tickets/{ticketId}")
    @PreAuthorize("hasRole('USER')")
    ApiResponse<?> ownTicket(
            @PathVariable @Positive long ticketId,
            JwtAuthenticationToken auth,
            HttpServletRequest request) {
        return ApiResponse.success(
                service.ownTicket(actor(auth), ticketId), RequestIdFilter.current(request));
    }

    @PostMapping("/staff/work-sessions")
    @PreAuthorize("hasRole('STAFF')")
    ApiResponse<?> start(
            @RequestBody @Valid StartRequest body,
            @RequestHeader("Idempotency-Key") String key,
            JwtAuthenticationToken auth,
            HttpServletRequest request) {
        return ApiResponse.success(
                service.start(actor(auth), body.windowId(), key, RequestIdFilter.current(request)),
                RequestIdFilter.current(request));
    }

    @DeleteMapping("/staff/work-sessions/{sessionId}")
    @PreAuthorize("hasRole('STAFF')")
    ApiResponse<?> end(
            @PathVariable @Positive long sessionId,
            @RequestBody @Valid EndRequest body,
            @RequestHeader("Idempotency-Key") String key,
            JwtAuthenticationToken auth,
            HttpServletRequest request) {
        return ApiResponse.success(
                service.end(
                        actor(auth),
                        sessionId,
                        body.version(),
                        key,
                        RequestIdFilter.current(request)),
                RequestIdFilter.current(request));
    }

    @GetMapping("/staff/work-sessions/current")
    @PreAuthorize("hasRole('STAFF')")
    ApiResponse<?> current(
            @RequestParam @Positive long windowId,
            JwtAuthenticationToken auth,
            HttpServletRequest request) {
        return ApiResponse.success(
                service.currentSession(actor(auth), windowId), RequestIdFilter.current(request));
    }

    @PostMapping("/staff/work-sessions/{sessionId}/call-next")
    @PreAuthorize("hasRole('STAFF')")
    ApiResponse<?> call(
            @PathVariable @Positive long sessionId,
            @RequestBody(required = false) CallNextRequest body,
            @RequestHeader("Idempotency-Key") String key,
            JwtAuthenticationToken auth,
            HttpServletRequest request) {
        return ApiResponse.success(
                service.callNext(
                        actor(auth),
                        sessionId,
                        body == null ? null : body.itemIds(),
                        key,
                        RequestIdFilter.current(request)),
                RequestIdFilter.current(request));
    }

    @PostMapping("/staff/queue-tickets/{ticketId}/{action:recall|miss|start|complete}")
    @PreAuthorize("hasRole('STAFF')")
    ApiResponse<?> change(
            @PathVariable @Positive long ticketId,
            @PathVariable String action,
            @RequestBody @Valid VersionRequest body,
            @RequestHeader("Idempotency-Key") String key,
            JwtAuthenticationToken auth,
            HttpServletRequest request) {
        return ApiResponse.success(
                service.change(
                        actor(auth),
                        body.sessionId(),
                        ticketId,
                        body.version(),
                        action.toUpperCase(java.util.Locale.ROOT),
                        body.resultCode(),
                        key,
                        RequestIdFilter.current(request)),
                RequestIdFilter.current(request));
    }

    @GetMapping("/admin/queues/overview")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<?> overview(
            @RequestParam @Positive long outletId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest request) {
        return ApiResponse.success(
                service.overview(outletId, date), RequestIdFilter.current(request));
    }
}
