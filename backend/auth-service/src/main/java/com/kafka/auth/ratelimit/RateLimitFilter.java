package com.kafka.auth.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka.auth.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String RATE_LIMIT_LIMIT_HEADER = "X-RateLimit-Limit";
    private static final String RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining";
    private static final String RATE_LIMIT_RESET_HEADER = "X-RateLimit-Reset";
    private static final int MAX_BUCKET_PART_LENGTH = 120;

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!rateLimitService.isEnabled()) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        RateLimitRule rule = resolveRule(request);
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            RateLimitResult result = rateLimitService.consume(rule.bucket(), rule.limit());
            applyHeaders(response, result);
            if (result.allowed()) {
                filterChain.doFilter(request, response);
                return;
            }
            writeTooManyRequests(request, response, result);
        } catch (RuntimeException exception) {
            log.warn("Rate limit check failed. Continuing request. path={} reason={}",
                    request.getRequestURI(),
                    exception.getClass().getSimpleName());
            log.debug("Rate limit failure detail.", exception);
            filterChain.doFilter(request, response);
        }
    }

    private RateLimitRule resolveRule(HttpServletRequest request) {
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        String path = request.getRequestURI();
        String clientKey = clientKey(request);
        if (isAuthWriteEndpoint(method, path)) {
            return new RateLimitRule("auth:" + clientKey + ":" + sanitize(method + ":" + path), rateLimitService.authLimit());
        }
        return new RateLimitRule("api:" + clientKey + ":" + sanitize(method + ":" + path), rateLimitService.apiLimit());
    }

    private boolean isAuthWriteEndpoint(String method, String path) {
        if ("POST".equals(method) && (
                "/api/auth/register".equals(path)
                        || "/api/auth/login".equals(path)
                        || "/api/auth/email/code".equals(path)
                        || "/api/auth/email/login".equals(path)
        )) {
            return true;
        }
        return "GET".equals(method)
                && ("/api/auth/oauth/kakao/authorize".equals(path) || "/api/auth/oauth/kakao/guide".equals(path));
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = Optional.ofNullable(request.getHeader("X-Forwarded-For"))
                .map(value -> value.split(",")[0].trim())
                .filter(value -> !value.isBlank())
                .orElse(null);
        if (forwardedFor != null) {
            return sanitize(forwardedFor);
        }
        return sanitize(Optional.ofNullable(request.getRemoteAddr()).orElse("unknown"));
    }

    private String sanitize(String value) {
        String sanitized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._:-]", "_");
        if (sanitized.length() <= MAX_BUCKET_PART_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, MAX_BUCKET_PART_LENGTH);
    }

    private void applyHeaders(HttpServletResponse response, RateLimitResult result) {
        response.setHeader(RATE_LIMIT_LIMIT_HEADER, String.valueOf(result.limit()));
        response.setHeader(RATE_LIMIT_REMAINING_HEADER, String.valueOf(result.remaining()));
        response.setHeader(RATE_LIMIT_RESET_HEADER, String.valueOf(result.resetAt().getEpochSecond()));
    }

    private void writeTooManyRequests(
            HttpServletRequest request,
            HttpServletResponse response,
            RateLimitResult result
    ) throws IOException {
        Instant now = Instant.now();
        long retryAfterSeconds = result.retryAfterSeconds(now);
        String traceId = resolveTraceId(request, response);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));

        ErrorResponse body = new ErrorResponse(
                now,
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "RATE_LIMIT_EXCEEDED",
                "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.",
                request.getRequestURI(),
                traceId,
                List.of("retryAfterSeconds: " + retryAfterSeconds)
        );
        objectMapper.writeValue(response.getWriter(), body);
    }

    private String resolveTraceId(HttpServletRequest request, HttpServletResponse response) {
        String responseRequestId = response.getHeader(REQUEST_ID_HEADER);
        if (responseRequestId != null && !responseRequestId.isBlank()) {
            return responseRequestId;
        }
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId != null && !requestId.isBlank()) {
            return requestId;
        }
        return UUID.randomUUID().toString();
    }

    private record RateLimitRule(String bucket, int limit) {
    }
}
