package com.civicflow.common.api;

import java.util.List;

/** Page data with one-based page numbering. */
public record PageResponse<T>(List<T> items, long page, long size, long total, long pages) {

    public PageResponse {
        items = List.copyOf(items);
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be at least 1");
        }
        if (total < 0) {
            throw new IllegalArgumentException("total must not be negative");
        }
        if (pages < 0) {
            throw new IllegalArgumentException("pages must not be negative");
        }
    }

    public static <T> PageResponse<T> of(List<T> items, long page, long size, long total) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be at least 1");
        }
        if (total < 0) {
            throw new IllegalArgumentException("total must not be negative");
        }
        long pages = total == 0 ? 0 : 1 + (total - 1) / size;
        return new PageResponse<>(items, page, size, total, pages);
    }
}
