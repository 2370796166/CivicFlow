package com.civicflow.appointment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.civicflow.appointment.client.*;
import com.civicflow.appointment.config.AppointmentProperties;
import com.civicflow.appointment.service.SlotAvailabilityService;
import com.civicflow.appointment.support.RedisKeyFactory;
import com.civicflow.common.api.ApiResponse;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.*;

class SlotAvailabilityServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void staleInventoryIsUnknownAndReadingIsBatched() {
        var resource = mock(ResourceSlotClient.class);
        var redis = mock(StringRedisTemplate.class);
        var now = Instant.parse("2026-10-11T01:00:00Z");
        var day = LocalDate.of(2026, 10, 11);
        var open =
                new ResourceSlotSnapshot(
                        "1",
                        "2",
                        "3",
                        "网点",
                        "事项",
                        day,
                        LocalTime.of(10, 0),
                        LocalTime.of(11, 0),
                        5,
                        now.minusSeconds(10),
                        now,
                        now.plusSeconds(7200),
                        now.plusSeconds(7200),
                        "OPEN",
                        3);
        var later =
                new ResourceSlotSnapshot(
                        "4",
                        "2",
                        "3",
                        "网点",
                        "事项",
                        day,
                        LocalTime.of(11, 0),
                        LocalTime.of(12, 0),
                        5,
                        now.plusSeconds(600),
                        now,
                        now.plusSeconds(7200),
                        now.plusSeconds(7200),
                        "SCHEDULED",
                        3);
        when(resource.getUserCandidates(2, 3, day))
                .thenReturn(ApiResponse.success(List.of(open, later), "r"));
        when(redis.executePipelined(any(RedisCallback.class)))
                .thenReturn(List.of(List.of("2", "2"), List.of("5", "3")));
        var service =
                new SlotAvailabilityService(
                        resource,
                        redis,
                        new RedisKeyFactory("test"),
                        Clock.fixed(now, ZoneOffset.UTC),
                        new AppointmentProperties());
        var rows = service.list(2, 3, day);
        assertEquals("UNKNOWN", rows.get(0).availability());
        assertNull(rows.get(0).remaining());
        assertEquals("NOT_RELEASED", rows.get(1).availability());
        verify(resource, times(1)).getUserCandidates(2, 3, day);
        verify(redis, times(1)).executePipelined(any(RedisCallback.class));
        when(redis.executePipelined(any(RedisCallback.class)))
                .thenThrow(new IllegalStateException("unavailable"));
        assertEquals("UNKNOWN", service.list(2, 3, day).get(0).availability());
        assertThrows(
                com.civicflow.common.exception.BusinessException.class,
                () -> service.list(2, 3, day.plusDays(31)));
    }
}
