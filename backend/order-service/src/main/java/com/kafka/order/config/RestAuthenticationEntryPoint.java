package com.kafka.order.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 인증되지 않은 요청(토큰 없음·만료·변조)에 401 + JSON 본문을 돌려준다.
 *
 * <p>미설정 시 Spring Security 기본 동작은 <b>403 + 빈 본문</b>이라 두 가지 문제가 생긴다:
 * 클라이언트가 "로그인 필요"(401)와 "권한 없음"(403)을 구분하지 못하고, 빈 본문을 JSON 파싱하다
 * 깨진다. 인증은 됐지만 권한이 부족한 경우는 그대로 403(AccessDeniedHandler 소관)으로 남긴다.</p>
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("code", "UNAUTHORIZED");
        body.put("message", "인증이 필요합니다. 다시 로그인해주세요.");
        body.put("path", request.getRequestURI());
        body.put("traceId", UUID.randomUUID().toString());
        body.put("details", List.of());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
