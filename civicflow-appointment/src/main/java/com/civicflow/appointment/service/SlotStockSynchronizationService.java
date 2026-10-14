package com.civicflow.appointment.service;

import com.civicflow.appointment.client.ResourceSlotClient;
import com.civicflow.appointment.client.ResourceSlotSnapshot;
import com.civicflow.appointment.config.AppointmentProperties;
import com.civicflow.appointment.error.AppointmentErrorCode;
import com.civicflow.appointment.mapper.StockReconciliationMapper;
import com.civicflow.appointment.support.RedisStockRepository;
import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.exception.BusinessException;
import java.time.Clock;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class SlotStockSynchronizationService {
    private final ResourceSlotClient resource;
    private final RedisStockRepository redis;
    private final StockReconciliationMapper facts;
    private final AppointmentProperties properties;
    private final Clock clock;

    public SlotStockSynchronizationService(
            ResourceSlotClient resource,
            RedisStockRepository redis,
            StockReconciliationMapper facts,
            AppointmentProperties properties,
            Clock clock) {
        this.resource = resource;
        this.redis = redis;
        this.facts = facts;
        this.properties = properties;
        this.clock = clock;
    }

    public void synchronize(long slotId) {
        var response = resource.getSnapshot(slotId);
        if (response == null
                || !CommonErrorCode.SUCCESS.code().equals(response.code())
                || response.data() == null)
            throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
        if (!Long.toString(slotId).equals(response.data().slotId()))
            throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
        synchronizeSnapshot(response.data());
    }

    public void synchronizeSnapshot(ResourceSlotSnapshot snapshot) {
        var slot = snapshot.toStockSnapshot();
        if (slot.configVersion() < 1
                || slot.totalQuota() < 0
                || !Set.of("DRAFT", "SCHEDULED", "OPEN", "SUSPENDED", "CLOSED")
                        .contains(slot.status())
                || !slot.closeAt().isAfter(slot.releaseAt()))
            throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
        var now = clock.instant();
        var expiresAt = slot.closeAt().plus(properties.getStock().getRetentionAfterClose());
        if (!expiresAt.isAfter(now)) return;
        var result = redis.synchronizeConfiguration(slot, now, expiresAt, false);
        if (result.code() == 5 && facts.countSlotHistory(slot.slotId()) == 0) {
            // With no stock, no request could have charged it. Lua atomically rechecks
            // absence/pending.
            result = redis.synchronizeConfiguration(slot, now, expiresAt, true);
        }
        if (result.code() == 5)
            throw new BusinessException(AppointmentErrorCode.STOCK_REBUILD_UNSAFE);
        if (result.code() != 0 && result.code() != 1 && result.code() != 2)
            throw new BusinessException(AppointmentErrorCode.STOCK_INVARIANT_BROKEN);
    }
}
