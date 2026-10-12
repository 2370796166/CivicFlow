package com.civicflow.appointment.service;

import com.civicflow.appointment.convert.AppointmentConverter;
import com.civicflow.appointment.dto.response.AppointmentResponse;
import com.civicflow.appointment.entity.AppointmentOrderEntity;
import com.civicflow.appointment.enums.AppointmentStatus;
import com.civicflow.appointment.mapper.AppointmentOrderMapper;
import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.api.PageResponse;
import com.civicflow.common.exception.BusinessException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminAppointmentQueryService {
    private final NamedParameterJdbcTemplate jdbc;
    private final AppointmentOrderMapper orders;

    public AdminAppointmentQueryService(
            NamedParameterJdbcTemplate jdbc, AppointmentOrderMapper orders) {
        this.jdbc = jdbc;
        this.orders = orders;
    }

    public PageResponse<AppointmentResponse> appointments(
            Long outletId,
            Long itemId,
            Long userId,
            LocalDate from,
            LocalDate to,
            AppointmentStatus status,
            int page,
            int size) {
        if (from != null && to != null && to.isBefore(from))
            throw new BusinessException(CommonErrorCode.VALIDATION);
        var p = new MapSqlParameterSource();
        String where = " WHERE 1=1";
        if (outletId != null) {
            where += " AND outlet_id=:outlet";
            p.addValue("outlet", outletId);
        }
        if (itemId != null) {
            where += " AND item_id=:item";
            p.addValue("item", itemId);
        }
        if (userId != null) {
            where += " AND user_id=:user";
            p.addValue("user", userId);
        }
        if (from != null) {
            where += " AND service_date>=:from";
            p.addValue("from", from);
        }
        if (to != null) {
            where += " AND service_date<=:to";
            p.addValue("to", to);
        }
        if (status != null) {
            where += " AND status=:status";
            p.addValue("status", status.name());
        }
        long count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM appointment_order" + where, p, Long.class);
        p.addValue("size", size).addValue("offset", (long) (page - 1) * size);
        // A bounded ID query avoids copying mapper conversion rules into reporting code.
        List<Long> ids =
                jdbc.queryForList(
                        "SELECT id FROM appointment_order"
                                + where
                                + " ORDER BY service_date DESC,slot_start_time DESC,id DESC LIMIT :size OFFSET :offset",
                        p,
                        Long.class);
        var records =
                ids.isEmpty() ? List.<AppointmentOrderEntity>of() : orders.selectBatchIds(ids);
        var byId =
                records.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        AppointmentOrderEntity::getId,
                                        java.util.function.Function.identity()));
        return PageResponse.of(
                ids.stream().map(byId::get).map(AppointmentConverter::toResponse).toList(),
                page,
                size,
                count);
    }

    public AppointmentResponse get(long id) {
        var order = orders.selectById(id);
        if (order == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return AppointmentConverter.toResponse(order);
    }

    public record Log(
            String id,
            String appointmentId,
            String actorId,
            String actorType,
            String operation,
            String fromStatus,
            String toStatus,
            String requestId,
            Instant occurredAt) {}

    public PageResponse<Log> logs(Long appointmentId, String requestId, int page, int size) {
        var p = new MapSqlParameterSource();
        String where = " WHERE 1=1";
        if (appointmentId != null) {
            where += " AND appointment_id=:appointment";
            p.addValue("appointment", appointmentId);
        }
        if (requestId != null && !requestId.isBlank()) {
            where += " AND request_id=:request";
            p.addValue("request", requestId);
        }
        long count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM appointment_operation_log" + where, p, Long.class);
        p.addValue("size", size).addValue("offset", (long) (page - 1) * size);
        var rows =
                jdbc.query(
                        "SELECT id,appointment_id,actor_id,actor_type,operation,from_status,to_status,request_id,occurred_at FROM appointment_operation_log"
                                + where
                                + " ORDER BY occurred_at DESC,id DESC LIMIT :size OFFSET :offset",
                        p,
                        (r, n) ->
                                new Log(
                                        r.getString("id"),
                                        r.getString("appointment_id"),
                                        r.getString("actor_id"),
                                        r.getString("actor_type"),
                                        r.getString("operation"),
                                        r.getString("from_status"),
                                        r.getString("to_status"),
                                        r.getString("request_id"),
                                        r.getTimestamp("occurred_at").toInstant()));
        return PageResponse.of(rows, page, size, count);
    }

    public record Run(
            String id, String status, String triggerType, Instant startedAt, Instant completedAt) {}

    public PageResponse<Run> runs(int page, int size) {
        var p =
                new MapSqlParameterSource()
                        .addValue("size", size)
                        .addValue("offset", (long) (page - 1) * size);
        long count =
                jdbc.queryForObject("SELECT COUNT(*) FROM stock_reconciliation_run", p, Long.class);
        var rows =
                jdbc.query(
                        "SELECT id,status,trigger_type,started_at,completed_at FROM stock_reconciliation_run ORDER BY started_at DESC,id DESC LIMIT :size OFFSET :offset",
                        p,
                        (r, n) ->
                                new Run(
                                        r.getString("id"),
                                        r.getString("status"),
                                        r.getString("trigger_type"),
                                        r.getTimestamp("started_at").toInstant(),
                                        r.getTimestamp("completed_at") == null
                                                ? null
                                                : r.getTimestamp("completed_at").toInstant()));
        return PageResponse.of(rows, page, size, count);
    }

    public record Detail(
            String slotId,
            int configuredTotal,
            int expectedRemaining,
            Integer actualRemaining,
            Integer diff,
            String classification,
            String repairStatus) {}

    public PageResponse<Detail> details(long runId, int page, int size) {
        var p =
                new MapSqlParameterSource("id", runId)
                        .addValue("size", size)
                        .addValue("offset", (long) (page - 1) * size);
        if (jdbc.queryForObject(
                        "SELECT COUNT(*) FROM stock_reconciliation_run WHERE id=:id", p, Long.class)
                == 0) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        long count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM stock_reconciliation_detail WHERE run_id=:id",
                        p,
                        Long.class);
        var rows =
                jdbc.query(
                        "SELECT slot_id,configured_total,expected_remaining,actual_remaining,diff,classification,repair_status FROM stock_reconciliation_detail WHERE run_id=:id ORDER BY id LIMIT :size OFFSET :offset",
                        p,
                        (r, n) ->
                                new Detail(
                                        r.getString("slot_id"),
                                        r.getInt("configured_total"),
                                        r.getInt("expected_remaining"),
                                        r.getObject("actual_remaining", Integer.class),
                                        r.getObject("diff", Integer.class),
                                        r.getString("classification"),
                                        r.getString("repair_status")));
        return PageResponse.of(rows, page, size, count);
    }

    public record Statistic(String status, long count) {}

    public List<Statistic> statistics(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || to.isAfter(from.plusDays(366)))
            throw new BusinessException(CommonErrorCode.VALIDATION);
        return jdbc.query(
                "SELECT status,COUNT(*) AS total FROM appointment_order WHERE service_date>=:from AND service_date<=:to GROUP BY status",
                new MapSqlParameterSource("from", from).addValue("to", to),
                (r, n) -> new Statistic(r.getString("status"), r.getLong("total")));
    }
}
