package com.civicflow.queue;

import java.sql.Timestamp;
import java.time.Instant;

public final class H2UtcClock {
    private H2UtcClock() {}

    public static Timestamp now(int precision) {
        return Timestamp.from(Instant.now());
    }
}
