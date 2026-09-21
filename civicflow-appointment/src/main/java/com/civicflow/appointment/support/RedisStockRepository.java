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
}
