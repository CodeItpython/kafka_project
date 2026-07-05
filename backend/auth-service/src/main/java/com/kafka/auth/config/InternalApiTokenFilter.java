package com.kafka.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards the {@code /api/internal/**} endpoints, which are meant only for
 * service-to-service calls (chat-service reads user data + writes audit rows here).
 * Callers must present the shared {@code app.internal.api-token}; the value is
 * compared in constant time. These endpoints must never be exposed to the public
 * gateway — only trusted services on the internal network should reach them.
 */
@Component
public class InternalApiTokenFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Internal-Api-Token";
    private static final String INTERNAL_PREFIX = "/api/internal/";

    private final byte[] expectedToken;

    public InternalApiTokenFilter(@Value("${app.internal.api-token}") String token) {
        this.expectedToken = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith(INTERNAL_PREFIX)) {
            String provided = request.getHeader(HEADER);
            byte[] providedBytes = provided == null ? new byte[0] : provided.getBytes(StandardCharsets.UTF_8);
            if (expectedToken.length == 0 || !MessageDigest.isEqual(providedBytes, expectedToken)) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\":\"invalid_internal_token\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
