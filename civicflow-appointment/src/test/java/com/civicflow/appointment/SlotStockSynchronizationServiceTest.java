package com.civicflow.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.civicflow.appointment.client.ResourceSlotClient;
import com.civicflow.appointment.client.ResourceSlotSnapshot;
import com.civicflow.appointment.config.AppointmentProperties;
import com.civicflow.appointment.error.AppointmentErrorCode;
import com.civicflow.appointment.mapper.StockReconciliationMapper;
import com.civicflow.appointment.service.SlotStockSynchronizationService;
import com.civicflow.appointment.support.LuaResult;
import com.civicflow.appointment.support.RedisStockRepository;
import com.civicflow.common.api.ApiResponse;
import com.civicflow.common.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SlotStockSynchronizationServiceTest {
    private final ResourceSlotClient resource = mock(ResourceSlotClient.class);
    private final RedisStockRepository redis = mock(RedisStockRepository.class);
    private final StockReconciliationMapper facts = mock(StockReconciliationMapper.class);
    private final Instant now = Instant.parse("2026-10-14T00:00:00Z");
    private final SlotStockSynchronizationService service =
            new SlotStockSynchronizationService(
                    resource,
                    redis,
                    facts,
                    new AppointmentProperties(),
                    Clock.fixed(now, ZoneOffset.UTC));

    private void snapshot() {
        when(resource.getSnapshot(1))
                .thenReturn(
                        ApiResponse.success(
                                new ResourceSlotSnapshot(
                                        "1",
                                        "2",
                                        "3",
                                        "outlet",
                                        "item",
                                        LocalDate.of(2026, 10, 14),
                                        LocalTime.of(9, 0),
                                        LocalTime.of(10, 0),
                                        10,
                                        now.minusSeconds(60),
                                        now,
                                        now.plusSeconds(3600),
                                        now.plusSeconds(3600),
                                        "OPEN",
                                        2),
                                "test"));
    }

    @Test
    void missingAfterReleaseRequiresNoHistoricalRequestsOrdersOrReleases() {
        snapshot();
        when(redis.synchronizeConfiguration(any(), any(), any(), eq(false)))
                .thenReturn(new LuaResult(5, -1, 2));
        when(facts.countSlotHistory(1)).thenReturn(0L);
        when(redis.synchronizeConfiguration(any(), any(), any(), eq(true)))
                .thenReturn(new LuaResult(0, 10, 2));
        service.synchronize(1);
        verify(redis).synchronizeConfiguration(any(), any(), any(), eq(true));
    }

    @Test
    void usedSlotNeverRecreatesFullQuotaWhenRedisStockDisappears() {
        snapshot();
        when(redis.synchronizeConfiguration(any(), any(), any(), eq(false)))
                .thenReturn(new LuaResult(5, -1, 2));
        when(facts.countSlotHistory(1)).thenReturn(1L);
        var error = assertThrows(BusinessException.class, () -> service.synchronize(1));
        assertEquals(AppointmentErrorCode.STOCK_REBUILD_UNSAFE, error.errorCode());
        verify(redis, never()).synchronizeConfiguration(any(), any(), any(), eq(true));
    }

    @Test
    void pendingEvidenceBlocksInitializationEvenWhenDatabaseIsEmpty() {
        snapshot();
        when(redis.synchronizeConfiguration(any(), any(), any(), eq(false)))
                .thenReturn(new LuaResult(5, -1, 2));
        when(redis.synchronizeConfiguration(any(), any(), any(), eq(true)))
                .thenReturn(new LuaResult(5, -1, 2));
        assertEquals(
                AppointmentErrorCode.STOCK_REBUILD_UNSAFE,
                assertThrows(BusinessException.class, () -> service.synchronize(1)).errorCode());
    }
}
