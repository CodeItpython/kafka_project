package com.kafka.auth.ratelimit;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Atomic "increment and set TTL on first hit" counter backed by a single Redis
 * Lua script. Doing INCR and PEXPIRE as separate round-trips risks losing the
 * expire (e.g. crash/failover between calls), leaving an immortal key that
 * permanently blocks its bucket. The script guarantees the TTL is set exactly
 * when the counter is created.
 */
@Component
@RequiredArgsConstructor
public class RedisAtomicCounter {
    private static final RedisScript<Long> INCR_WITH_TTL = RedisScript.of(
            "local current = redis.call('INCR', KEYS[1]) "
                    + "if current == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
                    + "return current",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    /** Increments the key, sets the TTL on creation, and returns the new count. */
    public long incrementWithTtl(String key, Duration ttl) {
        long ttlMillis = Math.max(1, ttl.toMillis());
        Long current = redisTemplate.execute(INCR_WITH_TTL, List.of(key), Long.toString(ttlMillis));
        return current == null ? 1 : current;
    }
}
