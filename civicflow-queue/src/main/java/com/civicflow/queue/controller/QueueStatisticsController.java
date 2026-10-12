package com.civicflow.queue.controller;

import com.civicflow.common.api.*;
import com.civicflow.common.exception.BusinessException;
import com.civicflow.queue.config.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/admin/queues/statistics")
@PreAuthorize("hasRole('ADMIN')")
public class QueueStatisticsController {
    private final JdbcTemplate jdbc;

    public QueueStatisticsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record WindowStatistic(
            String windowId,
            long completedCount,
            Double meanWaitSeconds,
            Double meanServiceSeconds) {}

    @GetMapping
    ApiResponse<List<WindowStatistic>> get(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) @Positive Long outletId,
            HttpServletRequest request) {
        if (dateTo.isBefore(dateFrom) || dateTo.isAfter(dateFrom.plusDays(366)))
            throw new BusinessException(CommonErrorCode.VALIDATION);
        String sql =
                "SELECT called_window_id,COUNT(*) AS total,AVG(CASE WHEN called_at>=checked_in_at THEN TIMESTAMPDIFF(SECOND,checked_in_at,called_at) END) AS mean_wait,AVG(CASE WHEN completed_at>=serving_at THEN TIMESTAMPDIFF(SECOND,serving_at,completed_at) END) AS mean_service FROM queue_ticket WHERE status='COMPLETED' AND service_date>=? AND service_date<=?";
        var args = new java.util.ArrayList<Object>(List.of(dateFrom, dateTo));
        if (outletId != null) {
            sql += " AND outlet_id=?";
            args.add(outletId);
        }
        sql += " GROUP BY called_window_id ORDER BY called_window_id LIMIT 500";
        var rows =
                jdbc.query(
                        sql,
                        (r, n) ->
                                new WindowStatistic(
                                        r.getString("called_window_id"),
                                        r.getLong("total"),
                                        r.getObject("mean_wait", Double.class),
                                        r.getObject("mean_service", Double.class)),
                        args.toArray());
        return ApiResponse.success(rows, RequestIdFilter.current(request));
    }
}
