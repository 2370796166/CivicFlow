package com.civicflow.appointment.support;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.regex.Pattern;

public final class RedisKeyFactory {
    private static final Pattern DIMENSION = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private final String prefix;

    public RedisKeyFactory(String environment) {
        String normalized = Objects.requireNonNull(environment).trim().toLowerCase();
        if (!DIMENSION.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid CivicFlow Redis environment dimension");
        }
        this.prefix = "cf:v1:" + normalized + ":";
    }

    public StockKeys stockKeys(long itemId, LocalDate serviceDate, long slotId) {
        String tag = tag(itemId, serviceDate);
        return new StockKeys(prefix + "stock:" + tag + ":slot:" + slotId, tag);
    }

    public ReconciliationKeys reconciliationKeys(long itemId, LocalDate serviceDate, long slotId) {
        String tag = tag(itemId, serviceDate);
        return new ReconciliationKeys(
                prefix + "stock:" + tag + ":slot:" + slotId,
                prefix + "reservation-pending:" + tag + ":slot:" + slotId);
    }

    public String activeKey(long itemId, LocalDate serviceDate, long userId) {
        return prefix + "active:" + tag(itemId, serviceDate) + ":user:" + userId;
    }

    public ReservationKeys reservationKeys(
            long itemId, LocalDate serviceDate, long slotId, long userId, String reservationId) {
        String tag = tag(itemId, serviceDate);
        return new ReservationKeys(
                prefix + "stock:" + tag + ":slot:" + slotId,
                prefix + "active:" + tag + ":user:" + userId,
                prefix + "reservation:" + tag + ":" + reservationId,
                prefix + "reservation-pending:" + tag + ":slot:" + slotId,
                prefix + "compensated:" + tag + ":" + reservationId);
    }

    private static String tag(long itemId, LocalDate serviceDate) {
        if (itemId <= 0) {
            throw new IllegalArgumentException("itemId must be positive");
        }
        return "{" + itemId + ":" + BASIC_DATE.format(serviceDate) + "}";
    }

    public record StockKeys(String stock, String hashTag) {}

    public record ReconciliationKeys(String stock, String pending) {}

    public record ReservationKeys(
            String stock, String active, String reservation, String pending, String compensated) {}
}
