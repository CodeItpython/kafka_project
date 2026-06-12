package com.kafka.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String REQUEST_ID_MDC_KEY = "requestId";
    private static final String METHOD_MDC_KEY = "httpMethod";
    private static final String PATH_MDC_KEY = "path";
    private static final String STATUS_MDC_KEY = "status";
    private static final int MAX_REQUEST_ID_LENGTH = 80;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        String requestId = resolveRequestId(request);

        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        MDC.put(METHOD_MDC_KEY, request.getMethod());
        MDC.put(PATH_MDC_KEY, request.getRequestURI());
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = System.currentTimeMillis() - startedAt;
            MDC.put(STATUS_MDC_KEY, String.valueOf(response.getStatus()));
            log.info("HTTP request completed method={} path={} status={} durationMs={} requestId={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    elapsedMs,
                    requestId);
            MDC.clear();
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(REQUEST_ID_HEADER))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(this::truncate)
                .orElseGet(() -> UUID.randomUUID().toString());
    }

    private String truncate(String value) {
        if (value.length() <= MAX_REQUEST_ID_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_REQUEST_ID_LENGTH);
    }
}
