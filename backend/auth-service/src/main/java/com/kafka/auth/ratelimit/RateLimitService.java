package com.kafka.auth.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class RateLimitService {
    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;
    private final Clock clock = Clock.systemUTC();

    public RateLimitResult consume(String bucket, int limit) {
        Duration window = normalizedWindow();
        Instant now = Instant.now(clock);
        long windowSeconds = window.toSeconds();
        long windowId = now.getEpochSecond() / windowSeconds;
        Instant resetAt = Instant.ofEpochSecond((windowId + 1) * windowSeconds);
        String key = "rate-limit:" + bucket + ":" + windowId;

        Long used = redisTemplate.opsForValue().increment(key);
        long normalizedUsed = used == null ? 1 : used;
        if (normalizedUsed == 1) {
            redisTemplate.expire(key, window.plusSeconds(5));
        }

        long remaining = Math.max(0, limit - normalizedUsed);
        return new RateLimitResult(normalizedUsed <= limit, limit, normalizedUsed, remaining, resetAt);
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public int authLimit() {
        return Math.max(1, properties.getAuthLimit());
    }

    public int apiLimit() {
        return Math.max(1, properties.getApiLimit());
    }

    private Duration normalizedWindow() {
        Duration window = properties.getWindow();
        if (window == null || window.isZero() || window.isNegative()) {
            return Duration.ofMinutes(1);
        }
        if (window.getSeconds() < 1) {
            return Duration.ofSeconds(1);
        }
        return window;
    }
}
