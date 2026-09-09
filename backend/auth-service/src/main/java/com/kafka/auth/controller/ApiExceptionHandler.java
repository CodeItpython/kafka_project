package com.kafka.auth.controller;

import com.kafka.auth.dto.ErrorResponse;
import com.kafka.auth.ratelimit.TooManyRequestsException;
import com.kafka.auth.storage.ObjectNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(ObjectNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleObjectNotFound(ObjectNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "INVALID_STATE", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException exception, HttpServletRequest request) {
        long retryAfterSeconds = Math.max(1, exception.getRetryAfter().toSeconds());
        ResponseEntity<ErrorResponse> response = error(
                HttpStatus.TOO_MANY_REQUESTS,
                "RATE_LIMIT_EXCEEDED",
                exception.getMessage(),
                request,
                List.of("retryAfterSeconds: " + retryAfterSeconds)
        );
        return ResponseEntity.status(response.getStatusCode())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
                .body(response.getBody());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "입력값을 다시 확인해주세요.", request, details);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        // Spring MVC 표준 예외(파라미터 누락·타입 불일치·미지원 메서드 등)는 스스로 올바른 4xx 상태를
        // 들고 온다. catch-all 이 이걸 뭉뚱그려 500으로 만들면 클라이언트 오류가 서버 오류로 보고돼
        // 클라이언트가 무의미한 재시도를 하고 에러 지표도 오염된다. 예외를 하나씩 열거하는 대신
        // Spring 이 매긴 상태를 그대로 존중한다.
        if (exception instanceof org.springframework.web.ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            if (status.is4xxClientError()) {
                return error(status, "BAD_REQUEST", exception.getMessage(), request, List.of());
            }
        }
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "잠시 후 다시 시도해주세요.", request, List.of(), exception);
    }

    private ResponseEntity<ErrorResponse> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            List<String> details
    ) {
        return error(status, code, message, request, details, null);
    }

    private ResponseEntity<ErrorResponse> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            List<String> details,
            Exception exception
    ) {
        String traceId = UUID.randomUUID().toString();
        String path = request.getRequestURI();
        if (status.is5xxServerError()) {
            log.error("api_error traceId={} status={} code={} path={} message={}", traceId, status.value(), code, path, message, exception);
        } else {
            log.warn("api_error traceId={} status={} code={} path={} message={} details={}", traceId, status.value(), code, path, message, details);
        }
        return ResponseEntity.status(status).body(new ErrorResponse(
                Instant.now(),
                status.value(),
                code,
                message,
                path,
                traceId,
                details
        ));
    }
}
