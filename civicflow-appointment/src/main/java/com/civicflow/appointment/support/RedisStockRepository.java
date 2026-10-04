package com.civicflow.appointment.support;

import java.time.Instant;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisStockRepository {
    private static final DefaultRedisScript<List> INIT = script("lua/init_stock.lua");
    private static final DefaultRedisScript<List> ADJUST = script("lua/adjust_stock.lua");
    private static final DefaultRedisScript<List> RESERVE = script("lua/reserve_stock.lua");
    private static final DefaultRedisScript<List> COMPENSATE = script("lua/compensate_stock.lua");
    private static final DefaultRedisScript<List> MARK_PUBLISHED = script("lua/mark_published.lua");
    private static final DefaultRedisScript<List> MARK_PERSISTED = script("lua/mark_persisted.lua");
    private static final DefaultRedisScript<List> RECONCILIATION_SNAPSHOT =
            script("lua/reconciliation_snapshot.lua");
    private static final DefaultRedisScript<List> RECONCILIATION_REPAIR =
            script("lua/reconciliation_repair.lua");
    private static final DefaultRedisScript<Long> RELEASE_ACTIVE =
            longScript("lua/release_active.lua");

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory keyFactory;

    public RedisStockRepository(StringRedisTemplate redisTemplate, RedisKeyFactory keyFactory) {
        this.redisTemplate = redisTemplate;
        this.keyFactory = keyFactory;
    }

    public LuaResult initialize(SlotStockSnapshot snapshot, Instant now, Instant stockExpiresAt) {
        RedisKeyFactory.StockKeys keys =
                keyFactory.stockKeys(snapshot.itemId(), snapshot.serviceDate(), snapshot.slotId());
        return execute(
                INIT,
                List.of(keys.stock()),
                snapshot.slotId(),
                snapshot.totalQuota(),
                snapshot.status(),
                snapshot.releaseAt().toEpochMilli(),
                snapshot.closeAt().toEpochMilli(),
                snapshot.configVersion(),
                now.toEpochMilli(),
                stockExpiresAt.toEpochMilli());
    }

    public ReconciliationSnapshot reconciliationSnapshot(SlotStockSnapshot snapshot) {
        RedisKeyFactory.ReconciliationKeys keys =
                keyFactory.reconciliationKeys(
                        snapshot.itemId(), snapshot.serviceDate(), snapshot.slotId());
        List<?> values =
                redisTemplate.execute(
                        RECONCILIATION_SNAPSHOT, List.of(keys.stock(), keys.pending()));
        if (values == null || values.size() != 5) {
            throw new IllegalStateException("Invalid reconciliation snapshot result");
        }
        return new ReconciliationSnapshot(
                number(values.get(0)),
                number(values.get(1)),
                number(values.get(2)),
                number(values.get(3)),
                number(values.get(4)));
    }

    public LuaResult reconciliationRepair(
            SlotStockSnapshot snapshot, ReconciliationSnapshot before, int expected, Instant now) {
        RedisKeyFactory.ReconciliationKeys keys =
                keyFactory.reconciliationKeys(
                        snapshot.itemId(), snapshot.serviceDate(), snapshot.slotId());
        return execute(
                RECONCILIATION_REPAIR,
                List.of(keys.stock(), keys.pending()),
                before.configVersion(),
                before.remaining(),
                before.mutationSeq(),
                snapshot.totalQuota(),
                expected,
                now.toEpochMilli());
    }

    public String activeReservation(SlotStockSnapshot snapshot, long userId) {
        return redisTemplate
                .opsForValue()
                .get(keyFactory.activeKey(snapshot.itemId(), snapshot.serviceDate(), userId));
    }

    public void releaseActiveIfOwned(
            long itemId, java.time.LocalDate serviceDate, long userId, String reservationId) {
        Long result =
                redisTemplate.execute(
                        RELEASE_ACTIVE,
                        List.of(keyFactory.activeKey(itemId, serviceDate, userId)),
                        reservationId);
        if (result == null) {
            throw new IllegalStateException("Missing active guard release result");
        }
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }

    public record ReconciliationSnapshot(
            long total, long remaining, long configVersion, long mutationSeq, long pendingCount) {}

    public LuaResult adjust(SlotStockSnapshot snapshot, Instant now, Instant stockExpiresAt) {
        RedisKeyFactory.StockKeys keys =
                keyFactory.stockKeys(snapshot.itemId(), snapshot.serviceDate(), snapshot.slotId());
        return execute(
                ADJUST,
                List.of(keys.stock()),
                snapshot.slotId(),
                snapshot.totalQuota(),
                snapshot.status(),
                snapshot.releaseAt().toEpochMilli(),
                snapshot.closeAt().toEpochMilli(),
                snapshot.configVersion(),
                now.toEpochMilli(),
                stockExpiresAt.toEpochMilli());
    }

    public LuaResult reserve(
            SlotStockSnapshot snapshot,
            long userId,
            String reservationId,
            Instant now,
            Instant reservationExpiresAt,
            Instant activeExpiresAt) {
        RedisKeyFactory.ReservationKeys keys =
                keyFactory.reservationKeys(
                        snapshot.itemId(),
                        snapshot.serviceDate(),
                        snapshot.slotId(),
                        userId,
                        reservationId);
        return execute(
                RESERVE,
                List.of(keys.stock(), keys.active(), keys.reservation(), keys.pending()),
                reservationId,
                userId,
                snapshot.slotId(),
                snapshot.itemId(),
                snapshot.serviceDate(),
                now.toEpochMilli(),
                reservationExpiresAt.toEpochMilli(),
                snapshot.configVersion(),
                reservationExpiresAt.toEpochMilli(),
                activeExpiresAt.toEpochMilli());
    }

    public LuaResult compensate(
            SlotStockSnapshot snapshot,
            long userId,
            String reservationId,
            String reason,
            Instant now,
            Instant markerExpiresAt) {
        RedisKeyFactory.ReservationKeys keys =
                keyFactory.reservationKeys(
                        snapshot.itemId(),
                        snapshot.serviceDate(),
                        snapshot.slotId(),
                        userId,
                        reservationId);
        return execute(
                COMPENSATE,
                List.of(
                        keys.stock(),
                        keys.active(),
                        keys.reservation(),
                        keys.pending(),
                        keys.compensated()),
                reservationId,
                userId,
                snapshot.slotId(),
                reason,
                now.toEpochMilli(),
                snapshot.configVersion(),
                markerExpiresAt.toEpochMilli());
    }

    public LuaResult markPublished(
            SlotStockSnapshot snapshot,
            long userId,
            String reservationId,
            String eventId,
            Instant now,
            Instant nextRecoveryAt) {
        RedisKeyFactory.ReservationKeys keys =
                keyFactory.reservationKeys(
                        snapshot.itemId(),
                        snapshot.serviceDate(),
                        snapshot.slotId(),
                        userId,
                        reservationId);
        return execute(
                MARK_PUBLISHED,
                List.of(keys.reservation(), keys.pending(), keys.stock()),
                reservationId,
                eventId,
                now.toEpochMilli(),
                nextRecoveryAt.toEpochMilli());
    }

    public LuaResult markPersisted(
            SlotStockSnapshot snapshot,
            long userId,
            String reservationId,
            long appointmentId,
            Instant now,
            Instant retentionUntil) {
        RedisKeyFactory.ReservationKeys keys =
                keyFactory.reservationKeys(
                        snapshot.itemId(),
                        snapshot.serviceDate(),
                        snapshot.slotId(),
                        userId,
                        reservationId);
        return execute(
                MARK_PERSISTED,
                List.of(keys.reservation(), keys.pending(), keys.stock()),
                reservationId,
                appointmentId,
                now.toEpochMilli(),
                retentionUntil.toEpochMilli());
    }

    private LuaResult execute(
            DefaultRedisScript<List> script, List<String> keys, Object... arguments) {
        Object[] values = new Object[arguments.length];
        for (int index = 0; index < arguments.length; index++) {
            values[index] = String.valueOf(arguments[index]);
        }
        return LuaResult.from(redisTemplate.execute(script, keys, values));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static DefaultRedisScript<List> script(String path) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(List.class);
        return script;
    }

    private static DefaultRedisScript<Long> longScript(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }
}
