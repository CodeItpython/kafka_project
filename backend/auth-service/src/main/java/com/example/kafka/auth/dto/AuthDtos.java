package com.example.kafka.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8) String password,
            @NotBlank String name
    ) {
    }

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {
    }

    public record EmailCodeRequest(
            @Email @NotBlank String email
    ) {
    }

    public record EmailLoginRequest(
            @Email @NotBlank String email,
            @NotBlank String code,
            String name
    ) {
    }

    public record AuthResponse(
            String accessToken,
            String tokenType,
            UserResponse user
    ) {
    }

    public record UserResponse(
            Long id,
            String email,
            String name,
            String provider
    ) {
    }

    public record EmailCodeResponse(
            Instant expiresAt,
            String debugCode
    ) {
    }

    public record KakaoGuideResponse(
            String developersUrl,
            String redirectUri,
            String[] requiredSettings
    ) {
    }
}
