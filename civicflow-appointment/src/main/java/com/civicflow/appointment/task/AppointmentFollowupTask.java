package com.civicflow.appointment.task;

import com.civicflow.appointment.client.ResourceSlotClient;
import com.civicflow.appointment.config.AppointmentProperties;
import com.civicflow.appointment.mapper.AppointmentOrderMapper;
import com.civicflow.appointment.service.*;
import com.civicflow.appointment.support.RedisStockRepository;
import java.time.*;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AppointmentFollowupTask {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;
    private final UserMessageService messages;
    private final AppointmentStateService states;
    private final AppointmentOrderMapper orders;
    private final ResourceSlotClient resource;
    private final RedisStockRepository stock;
    private final AppointmentProperties properties;
    private final Clock clock;
    private final java.util.concurrent.atomic.AtomicLong noShowCursor =
            new java.util.concurrent.atomic.AtomicLong();

    public AppointmentFollowupTask(
            JdbcTemplate jdbc,
            UserMessageService messages,
            AppointmentStateService states,
            AppointmentOrderMapper orders,
            ResourceSlotClient resource,
            RedisStockRepository stock,
            AppointmentProperties properties,
            Clock clock) {
        this.jdbc = jdbc;
        this.messages = messages;
        this.states = states;
        this.orders = orders;
        this.resource = resource;
        this.stock = stock;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${civicflow.appointment.followup-delay:30s}")
    public void run() {
        if (!properties.getRecovery().isEnabled()) return;
        publishStateMessages();
        var local = clock.instant().atZone(ZONE);
        var candidates =
                jdbc.queryForList(
                        "SELECT id FROM appointment_order WHERE status='CONFIRMED' AND service_date<=? AND id>? ORDER BY id LIMIT 50",
                        Long.class,
                        local.toLocalDate(),
                        noShowCursor.get());
        noShowCursor.set(candidates.size() < 50 ? 0 : candidates.get(candidates.size() - 1));
        var snapshots =
                new java.util.HashMap<
                        Long, com.civicflow.appointment.client.ResourceSlotSnapshot>();
        for (Long id : candidates) {
            try {
                var order = orders.selectById(id);
                if (order == null
                        || order.getStatus()
                                != com.civicflow.appointment.enums.AppointmentStatus.CONFIRMED)
                    continue;
                var snapshot =
                        snapshots.computeIfAbsent(
                                order.getSlotId(),
                                slot -> {
                                    var response = resource.getSnapshot(slot);
                                    return response == null ? null : response.data();
                                });
                if (snapshot != null
                        && snapshot.checkInEnd() != null
                        && !clock.instant().isBefore(snapshot.checkInEnd())) {
                    states.noShow(id, order.getVersion(), snapshot.checkInEnd(), "no-show-" + id);
                }
            } catch (RuntimeException ex) {
                log.warn(
                        "Appointment followup deferred appointmentId={} type={}",
                        id,
                        ex.getClass().getSimpleName());
            }
        }
        for (Long id :
                jdbc.queryForList(
                        "SELECT appointment_id FROM appointment_active_release WHERE status='PENDING' ORDER BY appointment_id LIMIT 50",
                        Long.class)) {
            try {
                var order = orders.selectById(id);
                if (order != null) {
                    stock.releaseActiveIfOwned(
                            order.getItemId(),
                            order.getServiceDate(),
                            order.getUserId(),
                            order.getReservationId());
                    jdbc.update(
                            "UPDATE appointment_active_release SET status='DONE',completed_at=CURRENT_TIMESTAMP(3) WHERE appointment_id=? AND status='PENDING'",
                            id);
                }
            } catch (RuntimeException ex) {
                log.warn(
                        "Active guard release deferred appointmentId={} type={}",
                        id,
                        ex.getClass().getSimpleName());
            }
        }
        publishUpcoming(local);
    }

    public void publishStateMessages() {
        var names =
                Map.of(
                        "CREATE",
                        "预约已受理",
                        "CONFIRM",
                        "预约已确认",
                        "CANCEL",
                        "预约已取消",
                        "EXPIRE",
                        "预约确认已超时",
                        "CHECK_IN",
                        "签到成功",
                        "START_SERVING",
                        "开始办理",
                        "COMPLETE",
                        "办理完成",
                        "MARK_NO_SHOW",
                        "预约已爽约");
        var rows =
                jdbc.queryForList(
                        "SELECT l.id,l.appointment_id,l.operation,o.user_id,o.item_name_snapshot,o.outlet_name_snapshot FROM appointment_operation_log l JOIN appointment_order o ON o.id=l.appointment_id WHERE l.operation!='LINK_QUEUE_TICKET' AND NOT EXISTS(SELECT 1 FROM user_message m WHERE m.source_key=CONCAT('log:',l.id)) ORDER BY l.id LIMIT 100");
        for (var row : rows) {
            String operation = row.get("operation").toString();
            messages.send(
                    ((Number) row.get("user_id")).longValue(),
                    ((Number) row.get("appointment_id")).longValue(),
                    "log:" + row.get("id"),
                    names.getOrDefault(operation, "预约状态已更新"),
                    row.get("outlet_name_snapshot")
                            + " · "
                            + row.get("item_name_snapshot")
                            + "。请查看预约详情。");
        }
    }

    private void publishUpcoming(ZonedDateTime now) {
        var tomorrow = now.toLocalDate().plusDays(1);
        var candidates =
                jdbc.queryForList(
                        "SELECT id FROM appointment_order o WHERE status='CONFIRMED' AND (service_date>? OR (service_date=? AND slot_start_time>?)) AND service_date<=? AND NOT EXISTS(SELECT 1 FROM user_message m WHERE m.source_key=CONCAT('reminder:',o.id)) ORDER BY service_date,slot_start_time,id LIMIT 500",
                        Long.class,
                        now.toLocalDate(),
                        now.toLocalDate(),
                        now.toLocalTime(),
                        tomorrow);
        for (Long id : candidates) {
            var order = orders.selectById(id);
            if (order == null) continue;
            Instant start =
                    order.getServiceDate()
                            .atTime(order.getSlotStartTime())
                            .atZone(ZONE)
                            .toInstant();
            if (start.isAfter(clock.instant())
                    && !start.isAfter(
                            clock.instant().plus(properties.getRules().getReminderLead()))) {
                messages.send(
                        order.getUserId(),
                        id,
                        "reminder:" + id,
                        "办理时间即将到达",
                        order.getOutletNameSnapshot()
                                + " · "
                                + order.getItemNameSnapshot()
                                + "，请在 "
                                + order.getSlotStartTime()
                                + " 前往办理，并按预约详情完成签到。");
            }
        }
    }
}
