package com.kafka.shopping.controller;

import com.kafka.shopping.dto.ErrorResponse;
import com.kafka.shopping.naver.NaverNotConfiguredException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
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

    @ExceptionHandler(NaverNotConfiguredException.class)
    public ResponseEntity<ErrorResponse> handleNaverNotConfigured(NaverNotConfiguredException exception, HttpServletRequest request) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "NAVER_NOT_CONFIGURED", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .toList();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "입력값을 다시 확인해주세요.", request, details);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "잠시 후 다시 시도해주세요.", request, List.of(), exception);
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message, HttpServletRequest request, List<String> details) {
        return error(status, code, message, request, details, null);
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message, HttpServletRequest request, List<String> details, Exception exception) {
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
