package com.civicflow.appointment.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RedisKeyFactoryTest {
    @Test
    void includesVersionEnvironmentBusinessDimensionsAndSharedHashTag() {
        RedisKeyFactory factory = new RedisKeyFactory("staging-cn");
        RedisKeyFactory.ReservationKeys keys =
                factory.reservationKeys(
                        1903, LocalDate.of(2026, 9, 20), 9001, 8001, "reservation-1");

        assertEquals("cf:v1:staging-cn:stock:{1903:20260920}:slot:9001", keys.stock());
        assertEquals("cf:v1:staging-cn:active:{1903:20260920}:user:8001", keys.active());
        assertEquals(
                "cf:v1:staging-cn:reservation:{1903:20260920}:reservation-1", keys.reservation());
        assertEquals(
                "cf:v1:staging-cn:reservation-pending:{1903:20260920}:slot:9001", keys.pending());
    }

    @Test
    void rejectsUnsafeEnvironmentDimension() {
        assertThrows(IllegalArgumentException.class, () -> new RedisKeyFactory("prod:{evil}"));
    }
}
