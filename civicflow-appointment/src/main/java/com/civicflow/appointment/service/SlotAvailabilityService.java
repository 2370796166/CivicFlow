package com.civicflow.appointment.service;

import com.civicflow.appointment.client.ResourceSlotClient;
import com.civicflow.appointment.client.ResourceSlotSnapshot;
import com.civicflow.appointment.support.RedisKeyFactory;
import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class SlotAvailabilityService {
    private final ResourceSlotClient resource;
    private final StringRedisTemplate redis;
    private final RedisKeyFactory keys;
    private final Clock clock;
    private final com.civicflow.appointment.config.AppointmentProperties properties;

    public SlotAvailabilityService(
            ResourceSlotClient resource,
            StringRedisTemplate redis,
            RedisKeyFactory keys,
            Clock clock,
            com.civicflow.appointment.config.AppointmentProperties properties) {
        this.resource = resource;
        this.redis = redis;
        this.keys = keys;
        this.clock = clock;
        this.properties = properties;
    }

    public record Slot(
            ResourceSlotSnapshot configuration, Integer remaining, String availability) {}

    public List<Slot> list(long outletId, long itemId, LocalDate date) {
        LocalDate today = clock.instant().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        if (properties.getRules().getClosedDates().contains(date)) return List.of();
        if (date.isBefore(today)
                || date.isAfter(today.plusDays(properties.getRules().getAdvanceDays())))
            throw new BusinessException(CommonErrorCode.VALIDATION);
        List<ResourceSlotSnapshot> configurations;
        try {
            var response = resource.getUserCandidates(outletId, itemId, date);
            if (response == null || response.data() == null)
                throw new IllegalStateException("Missing slot candidates");
            configurations = response.data();
        } catch (RuntimeException ex) {
            throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
        }
        if (configurations.isEmpty()) return List.of();
        List<Object> values;
        try {
            values =
                    redis.executePipelined(
                            (RedisCallback<Object>)
                                    connection -> {
                                        for (var slot : configurations) {
                                            String key =
                                                    keys.stockKeys(
                                                                    Long.parseLong(slot.itemId()),
                                                                    slot.serviceDate(),
                                                                    Long.parseLong(slot.slotId()))
                                                            .stock();
                                            connection
                                                    .hashCommands()
                                                    .hMGet(
                                                            key.getBytes(StandardCharsets.UTF_8),
                                                            "remaining"
                                                                    .getBytes(
                                                                            StandardCharsets.UTF_8),
                                                            "configVersion"
                                                                    .getBytes(
                                                                            StandardCharsets
                                                                                    .UTF_8));
                                        }
                                        return null;
                                    });
        } catch (RuntimeException ex) {
            values = java.util.Collections.nCopies(configurations.size(), null);
        }
        List<Slot> result = new java.util.ArrayList<>();
        for (int i = 0; i < configurations.size(); i++) {
            var slot = configurations.get(i);
            Integer remaining = null;
            try {
                if (values.get(i) instanceof List<?> fields
                        && fields.size() == 2
                        && fields.get(0) != null
                        && fields.get(1) != null
                        && Long.parseLong(fields.get(1).toString()) == slot.configVersion()) {
                    int amount = Integer.parseInt(fields.get(0).toString());
                    if (amount >= 0 && amount <= slot.totalQuota()) remaining = amount;
                }
            } catch (NumberFormatException ignored) {
                /* A stale projection is never presented as available inventory. */
            }
            String state;
            if (!clock.instant().isBefore(slot.closeAt())
                    || !List.of("SCHEDULED", "OPEN").contains(slot.status())) state = "CLOSED";
            else if (clock.instant().isBefore(slot.releaseAt())) state = "NOT_RELEASED";
            else if (remaining == null) state = "UNKNOWN";
            else state = remaining == 0 ? "FULL" : "AVAILABLE";
            result.add(new Slot(slot, remaining, state));
        }
        return result;
    }
}
