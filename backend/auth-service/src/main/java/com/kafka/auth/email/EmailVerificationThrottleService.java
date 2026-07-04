package com.kafka.auth.email;

import com.kafka.auth.ratelimit.RedisAtomicCounter;
import com.kafka.auth.ratelimit.TooManyRequestsException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationThrottleService {
    private static final String SEND_COOLDOWN_PREFIX = "email-verification:send-cooldown:";
    private static final String VERIFY_FAILURE_PREFIX = "email-verification:verify-failures:";

    private final StringRedisTemplate redisTemplate;
    private final RedisAtomicCounter counter;
    private final EmailVerificationProperties properties;

    public void acquireSendPermit(String email) {
        Duration cooldown = normalizedDuration(properties.getResendCooldown(), Duration.ofSeconds(60));
        String key = sendCooldownKey(email);
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", cooldown);
            if (Boolean.FALSE.equals(acquired)) {
                throw new TooManyRequestsException(
                        "인증코드는 너무 자주 요청할 수 없습니다. 잠시 후 다시 시도해주세요.",
                        remainingTtl(key, cooldown)
                );
            }
        } catch (TooManyRequestsException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("Email verification send throttle failed open. email={} reason={}", email, exception.getClass().getSimpleName());
            log.debug("Email verification send throttle failure detail.", exception);
        }
    }

    public void releaseSendPermit(String email) {
        try {
            redisTemplate.delete(sendCooldownKey(email));
        } catch (RuntimeException exception) {
            log.debug("Unable to release email verification send permit. email={}", email, exception);
        }
    }

    public void assertVerifyAllowed(String email) {
        try {
            String value = redisTemplate.opsForValue().get(verifyFailureKey(email));
            if (value == null || value.isBlank()) {
                return;
            }
            long failures = Long.parseLong(value);
            if (failures >= maxVerifyAttempts()) {
                throw new TooManyRequestsException(
                        "인증 실패 횟수가 너무 많습니다. 새 인증코드를 요청해주세요.",
                        remainingTtl(verifyFailureKey(email), normalizedDuration(properties.getVerifyAttemptWindow(), Duration.ofMinutes(10)))
                );
            }
        } catch (TooManyRequestsException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("Email verification attempt throttle failed open. email={} reason={}", email, exception.getClass().getSimpleName());
            log.debug("Email verification attempt throttle failure detail.", exception);
        }
    }

    public void recordVerifyFailure(String email) {
        Duration window = normalizedDuration(properties.getVerifyAttemptWindow(), Duration.ofMinutes(10));
        String key = verifyFailureKey(email);
        try {
            // Atomic INCR + first-hit expire so the window can't be lost mid-way,
            // which would otherwise leave the counter without a TTL forever.
            long failures = counter.incrementWithTtl(key, window);
            if (failures >= maxVerifyAttempts()) {
                throw new TooManyRequestsException(
                        "인증 실패 횟수가 너무 많습니다. 새 인증코드를 요청해주세요.",
                        remainingTtl(key, window)
                );
            }
        } catch (TooManyRequestsException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("Email verification failure count failed open. email={} reason={}", email, exception.getClass().getSimpleName());
            log.debug("Email verification failure count detail.", exception);
        }
    }

    public void clearVerifyFailures(String email) {
        try {
            redisTemplate.delete(verifyFailureKey(email));
        } catch (RuntimeException exception) {
            log.debug("Unable to clear email verification failures. email={}", email, exception);
        }
    }

    private int maxVerifyAttempts() {
        return Math.max(1, properties.getMaxVerifyAttempts());
    }

    private Duration remainingTtl(String key, Duration fallback) {
        Long seconds = redisTemplate.getExpire(key);
        if (seconds == null || seconds < 1) {
            return fallback;
        }
        return Duration.ofSeconds(seconds);
    }

    private Duration normalizedDuration(Duration value, Duration fallback) {
        if (value == null || value.isZero() || value.isNegative()) {
            return fallback;
        }
        return value;
    }

    private String sendCooldownKey(String email) {
        return SEND_COOLDOWN_PREFIX + email;
    }

    private String verifyFailureKey(String email) {
        return VERIFY_FAILURE_PREFIX + email;
    }
}
