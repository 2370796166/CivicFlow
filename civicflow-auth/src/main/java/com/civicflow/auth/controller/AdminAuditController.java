package com.civicflow.auth.controller;

import com.civicflow.auth.config.RequestIdFilter;
import com.civicflow.common.api.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.*;
import java.time.Instant;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/admin/audit/auth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {
    private final NamedParameterJdbcTemplate jdbc;

    public AdminAuditController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Audit(
            String id,
            String actorId,
            String targetId,
            String operation,
            String category,
            String requestId,
            Instant occurredAt) {}

    @GetMapping
    @Transactional(readOnly = true)
    ApiResponse<PageResponse<Audit>> list(
            @RequestParam(required = false) @Size(max = 64) String requestId,
            @RequestParam(defaultValue = "1") @Min(1) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        var params = new MapSqlParameterSource();
        String where = "";
        if (requestId != null && !requestId.isBlank()) {
            where = " WHERE request_id=:request";
            params.addValue("request", requestId);
        }
        long count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM auth_security_audit" + where, params, Long.class);
        params.addValue("size", size).addValue("offset", (long) (page - 1) * size);
        var rows =
                jdbc.query(
                        "SELECT id,actor_user_id,target_user_id,action,outcome,request_id,occurred_at FROM auth_security_audit"
                                + where
                                + " ORDER BY occurred_at DESC,id DESC LIMIT :size OFFSET :offset",
                        params,
                        (r, n) ->
                                new Audit(
                                        r.getString("id"),
                                        r.getString("actor_user_id"),
                                        r.getString("target_user_id"),
                                        r.getString("action"),
                                        r.getString("outcome"),
                                        r.getString("request_id"),
                                        r.getTimestamp("occurred_at").toInstant()));
        return ApiResponse.success(
                PageResponse.of(rows, page, size, count), RequestIdFilter.current(request));
    }
}
