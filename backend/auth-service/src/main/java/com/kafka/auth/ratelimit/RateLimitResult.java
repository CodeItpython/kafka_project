package com.kafka.auth.ratelimit;

import java.time.Instant;

public record RateLimitResult(
        boolean allowed,
        int limit,
        long used,
        long remaining,
        Instant resetAt
) {
    public long retryAfterSeconds(Instant now) {
        if (resetAt == null || now == null || !resetAt.isAfter(now)) {
            return 1;
        }
        return Math.max(1, resetAt.getEpochSecond() - now.getEpochSecond());
    }
}
