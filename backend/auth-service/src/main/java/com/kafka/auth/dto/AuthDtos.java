package com.kafka.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    /** 비밀번호 재설정 링크 요청(가입 이메일로 발송). */
    public record PasswordResetRequest(
            @Email @NotBlank String email
    ) {
    }

    /** 재설정 링크의 토큰으로 새 비밀번호를 확정. */
    public record PasswordResetConfirmRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8) String newPassword
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
            String profileImageUrl,
            String theme,
            NotificationSettingsResponse notificationSettings
    ) {
    }

    /** 알림 설정(방해금지 시간대 + 알림 종류). */
    public record NotificationSettingsResponse(
            boolean dndEnabled,
            int dndStart,
            int dndEnd,
            boolean notifyMessages,
            boolean notifyMentionsOnly,
            boolean notifyMarketing
    ) {
    }

    public record UpdateNotificationSettingsRequest(
            boolean dndEnabled,
            @Min(0) @Max(23) int dndStart,
            @Min(0) @Max(23) int dndEnd,
            boolean notifyMessages,
            boolean notifyMentionsOnly,
            boolean notifyMarketing
    ) {
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 500) String statusMessage
    ) {
    }

    public record UpdateThemeRequest(
            @NotBlank @Pattern(regexp = "light|dark|system", message = "테마는 light/dark/system 중 하나여야 합니다.") String theme
    ) {
    }

    public record UserProfileResponse(
            Long id,
            String email,
            String name,
            String provider,
            String statusMessage,
            String profileImageUrl,
            String theme,
            NotificationSettingsResponse notificationSettings,
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
