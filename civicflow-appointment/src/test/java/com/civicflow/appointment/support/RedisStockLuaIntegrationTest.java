package com.civicflow.appointment.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.civicflow.appointment.config.AppointmentProperties;
import com.civicflow.appointment.error.AppointmentErrorCode;
import com.civicflow.appointment.service.ReservationStockService;
import com.civicflow.appointment.service.impl.ReservationStockServiceImpl;
import com.civicflow.common.exception.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class RedisStockLuaIntegrationTest {
    private static final Instant TEST_NOW = Instant.now();
    private static final Instant PREHEAT_AT = TEST_NOW.minus(Duration.ofHours(2));
    private static final Instant RELEASE_AT = TEST_NOW.minus(Duration.ofHours(1));
    private static final Instant AFTER_RELEASE = TEST_NOW;
    private static final Instant CLOSE_AT = TEST_NOW.plus(Duration.ofDays(4));
    private static final LocalDate SERVICE_DATE =
            LocalDate.ofInstant(CLOSE_AT, ZoneId.of("Asia/Shanghai"));

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4.11-alpine"))
                    .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisStockRepository repository;
    private static AppointmentProperties properties;

    @BeforeAll
    static void connect() {
        connectionFactory =
                new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        repository = new RedisStockRepository(redisTemplate, new RedisKeyFactory("integration"));
        properties = new AppointmentProperties();
        properties.getStock().setReservationTtl(Duration.ofMinutes(10));
        properties.getStock().setRetentionAfterClose(Duration.ofDays(2));
    }

    @AfterEach
    void flushRedis() {
        connectionFactory.getConnection().serverCommands().flushDb();
    }

    @AfterAll
    static void disconnect() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void oneHundredConcurrentRequestsSucceedExactlyQuotaTimes() throws Exception {
        int quota = 17;
        SlotStockSnapshot snapshot = snapshot(1001, quota, RELEASE_AT, 1);
        initialize(snapshot);
        ReservationStockService service = serviceAt(AFTER_RELEASE);
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < 100; index++) {
                long userId = 10_000L + index;
                String reservationId = UUID.randomUUID().toString();
                futures.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    try {
                                        service.reserve(snapshot, userId, reservationId);
                                        return true;
                                    } catch (BusinessException exception) {
                                        assertEquals(
                                                AppointmentErrorCode.STOCK_EMPTY,
                                                exception.errorCode());
                                        return false;
                                    }
                                }));
            }
            start.countDown();
            int successes = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(20, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            assertEquals(quota, successes);
            assertEquals("0", remaining(snapshot));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsAnotherReservationForSameUserAndReplaysSameReservation() {
        SlotStockSnapshot snapshot = snapshot(1002, 2, RELEASE_AT, 1);
        initialize(snapshot);
        ReservationStockService service = serviceAt(AFTER_RELEASE);

        ReservationStockService.ReservationResult first =
                service.reserve(snapshot, 2001, "reservation-a");
        ReservationStockService.ReservationResult replay =
                service.reserve(snapshot, 2001, "reservation-a");
        BusinessException duplicate =
                assertThrows(
                        BusinessException.class,
                        () -> service.reserve(snapshot, 2001, "reservation-b"));

        assertFalse(first.idempotent());
        assertTrue(replay.idempotent());
        assertEquals(AppointmentErrorCode.DUP_ACTIVE, duplicate.errorCode());
        assertEquals("1", remaining(snapshot));
    }

    @Test
    void rejectsBeforeReleaseAndWhenStockIsZero() {
        SlotStockSnapshot future = snapshot(1003, 1, TEST_NOW.plus(Duration.ofHours(2)), 1);
        initialize(future);
        BusinessException notReleased =
                assertThrows(
                        BusinessException.class,
                        () -> serviceAt(AFTER_RELEASE).reserve(future, 3001, "not-released"));
        assertEquals(AppointmentErrorCode.SLOT_NOT_OPEN, notReleased.errorCode());

        SlotStockSnapshot empty = snapshot(1004, 0, RELEASE_AT, 1);
        initialize(empty);
        BusinessException noStock =
                assertThrows(
                        BusinessException.class,
                        () -> serviceAt(AFTER_RELEASE).reserve(empty, 3002, "empty"));
        assertEquals(AppointmentErrorCode.STOCK_EMPTY, noStock.errorCode());
    }

    @Test
    void compensationRequiresCurrentReservationAndOnlyReleasesOnce() {
        SlotStockSnapshot snapshot = snapshot(1005, 1, RELEASE_AT, 1);
        initialize(snapshot);
        ReservationStockService service = serviceAt(AFTER_RELEASE);
        service.reserve(snapshot, 4001, "reservation-c");

        BusinessException wrong =
                assertThrows(
                        BusinessException.class,
                        () -> service.compensate(snapshot, 4001, "wrong-reservation", "TEST"));
        assertEquals(AppointmentErrorCode.STOCK_INVARIANT_BROKEN, wrong.errorCode());
        assertEquals("0", remaining(snapshot));

        ReservationStockService.CompensationResult released =
                service.compensate(snapshot, 4001, "reservation-c", "TEST");
        ReservationStockService.CompensationResult duplicate =
                service.compensate(snapshot, 4001, "reservation-c", "TEST");
        assertFalse(released.idempotent());
        assertTrue(duplicate.idempotent());
        assertEquals("1", remaining(snapshot));
    }

    @Test
    void preheatIsIdempotentAndContinuousAdjustmentPreservesDeductions() {
        SlotStockSnapshot versionOne = snapshot(1006, 3, RELEASE_AT, 1);
        initialize(versionOne);
        serviceAt(AFTER_RELEASE).reserve(versionOne, 5001, "reservation-d");

        LuaResult replay =
                repository.initialize(versionOne, AFTER_RELEASE, CLOSE_AT.plus(Duration.ofDays(2)));
        assertEquals(
                PreheatScriptResultCode.IDEMPOTENT, PreheatScriptResultCode.from(replay.code()));
        assertEquals(2, replay.remaining());

        SlotStockSnapshot versionTwo = snapshot(1006, 5, RELEASE_AT, 2);
        LuaResult requiresAdjust =
                repository.initialize(versionTwo, AFTER_RELEASE, CLOSE_AT.plus(Duration.ofDays(2)));
        assertEquals(
                PreheatScriptResultCode.REQUIRES_ADJUST,
                PreheatScriptResultCode.from(requiresAdjust.code()));
        LuaResult adjusted =
                repository.adjust(versionTwo, AFTER_RELEASE, CLOSE_AT.plus(Duration.ofDays(2)));
        assertEquals(
                PreheatScriptResultCode.APPLIED, PreheatScriptResultCode.from(adjusted.code()));
        assertEquals(4, adjusted.remaining());
    }

    @Test
    void terminalCleanupKeepsConsumedStockAndCannotDeleteNewReservation() {
        SlotStockSnapshot snapshot = snapshot(1008, 3, RELEASE_AT, 1);
        initialize(snapshot);
        ReservationStockService service = serviceAt(AFTER_RELEASE);
        service.reserve(snapshot, 6001, "completed-reservation");
        repository.releaseActiveIfOwned(
                snapshot.itemId(), snapshot.serviceDate(), 6001, "completed-reservation");
        assertEquals("2", remaining(snapshot));
        service.reserve(snapshot, 6001, "new-reservation");
        repository.releaseActiveIfOwned(
                snapshot.itemId(), snapshot.serviceDate(), 6001, "completed-reservation");
        assertEquals("new-reservation", repository.activeReservation(snapshot, 6001));
        assertEquals("1", remaining(snapshot));
    }

    @Test
    void refusesToInitializeMissingStockAfterRelease() {
        SlotStockSnapshot snapshot = snapshot(1007, 3, RELEASE_AT, 1);
        LuaResult result =
                repository.initialize(snapshot, AFTER_RELEASE, CLOSE_AT.plus(Duration.ofDays(2)));
        assertEquals(
                PreheatScriptResultCode.UNSAFE_MISSING,
                PreheatScriptResultCode.from(result.code()));
        assertEquals(null, remaining(snapshot));
    }

    private static void initialize(SlotStockSnapshot snapshot) {
        LuaResult result =
                repository.initialize(snapshot, PREHEAT_AT, CLOSE_AT.plus(Duration.ofDays(2)));
        assertEquals(PreheatScriptResultCode.APPLIED, PreheatScriptResultCode.from(result.code()));
    }

    private static ReservationStockService serviceAt(Instant instant) {
        return new ReservationStockServiceImpl(
                repository, properties, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static SlotStockSnapshot snapshot(
            long slotId, int quota, Instant releaseAt, long configVersion) {
        return new SlotStockSnapshot(
                slotId,
                7001,
                6001,
                SERVICE_DATE,
                quota,
                releaseAt,
                CLOSE_AT,
                "SCHEDULED",
                configVersion);
    }

    private static Object remaining(SlotStockSnapshot snapshot) {
        RedisKeyFactory.StockKeys keys =
                new RedisKeyFactory("integration")
                        .stockKeys(snapshot.itemId(), snapshot.serviceDate(), snapshot.slotId());
        return redisTemplate.opsForHash().get(keys.stock(), "remaining");
    }
}
