package com.civicflow.common.api;

import com.civicflow.common.exception.BusinessException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Bounded administrator queries; calendar dates use the business timezone. */
public record AdminQueryWindow(int page, int size, LocalDate dateFrom, LocalDate dateTo) {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    public AdminQueryWindow {
        if (page < 1
                || page > 1000000
                || size < 1
                || size > 100
                || (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo))) {
            throw new BusinessException(CommonErrorCode.VALIDATION);
        }
    }

    public long offset() {
        return (long) (page - 1) * size;
    }

    public Instant fromInstant() {
        return dateFrom == null ? null : dateFrom.atStartOfDay(ZONE).toInstant();
    }

    public Instant toExclusiveInstant() {
        return dateTo == null ? null : dateTo.plusDays(1).atStartOfDay(ZONE).toInstant();
    }
}
