package com.civicflow.queue.controller;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.civicflow.common.api.*;
import com.civicflow.common.exception.BusinessException;
import com.civicflow.queue.config.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/admin/queues/syncs")
@PreAuthorize("hasRole('ADMIN')")
public class QueueSyncOperationsController {
    private final JdbcTemplate jdbc;

    public QueueSyncOperationsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Sync(
            String id,
            String ticketId,
            String appointmentId,
            String targetStatus,
            String status,
            int attempts,
            String lastErrorCode) {}

    @GetMapping
    ApiResponse<PageResponse<Sync>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        long total =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM queue_state_sync WHERE status!='DONE'", Long.class);
        var rows =
                jdbc.query(
                        "SELECT * FROM queue_state_sync WHERE status!='DONE' ORDER BY next_attempt_at,id LIMIT ? OFFSET ?",
                        (r, n) ->
                                new Sync(
                                        r.getString("id"),
                                        r.getString("ticket_id"),
                                        r.getString("appointment_id"),
                                        r.getString("target_status"),
                                        r.getString("status"),
                                        r.getInt("attempts"),
                                        r.getString("last_error_code")),
                        size,
                        (long) (page - 1) * size);
        return ApiResponse.success(
                PageResponse.of(rows, page, size, total), RequestIdFilter.current(request));
    }

    public record Retry(@NotBlank @Size(max = 256) String reason) {}

    @PostMapping("/{id}/retry")
    @Transactional
    ApiResponse<Void> retry(
            @PathVariable @Positive long id,
            @Valid @RequestBody Retry body,
            JwtAuthenticationToken auth,
            HttpServletRequest request) {
        if (jdbc.update(
                        "UPDATE queue_state_sync SET status='PENDING',attempts=0,lease_owner=NULL,lease_until=NULL,next_attempt_at=CURRENT_TIMESTAMP(3) WHERE id=? AND status='NEEDS_ATTENTION'",
                        id)
                != 1) throw new BusinessException(CommonErrorCode.VALIDATION);
        jdbc.update(
                "INSERT INTO queue_sync_audit(id,sync_id,actor_id,request_id,reason) VALUES(?,?,?,?,?)",
                IdWorker.getId(),
                id,
                Long.parseLong(auth.getName()),
                RequestIdFilter.current(request),
                body.reason().trim());
        return ApiResponse.success(null, RequestIdFilter.current(request));
    }
}
