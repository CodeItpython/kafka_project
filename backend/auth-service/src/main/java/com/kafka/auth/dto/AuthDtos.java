package com.kafka.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
            @NotBlank @Pattern(regexp = "\\d{6}", message = "인증코드는 6자리 숫자입니다.") String code,
            String name
    ) {
    }

    public record ChangeEmailRequest(
            @Email @NotBlank String email,
            @NotBlank @Pattern(regexp = "\\d{6}", message = "인증코드는 6자리 숫자입니다.") String code
    ) {
    }

    /** 회원가입 중 이메일 소유 확인(로그인/계정생성 없이 코드만 검증). */
    public record EmailVerifyRequest(
            @Email @NotBlank String email,
            @NotBlank @Pattern(regexp = "\\d{6}", message = "인증코드는 6자리 숫자입니다.") String code
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
            String provider,
            String role,
            String statusMessage,
            String profileImageUrl
    ) {
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 500) String statusMessage
    ) {
    }

    public record UserProfileResponse(
            Long id,
            String email,
            String name,
            String provider,
            String statusMessage,
            String profileImageUrl,
            Instant createdAt,
            Instant updatedAt,
            java.util.List<UserProfileHistoryResponse> history
    ) {
    }

    public record UserProfileHistoryResponse(
            Long id,
            String name,
            String statusMessage,
            String profileImageUrl,
            String eventType,
            Instant createdAt
    ) {
    }

    public record EmailCodeResponse(
            Instant expiresAt,
            String sentTo
    ) {
    }

    public record KakaoGuideResponse(
            String developersUrl,
            String redirectUri,
            String[] requiredSettings
    ) {
    }
}
