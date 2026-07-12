package com.kafka.auth.service;

import com.kafka.auth.dto.AuthDtos.AuthResponse;
import com.kafka.auth.dto.AuthDtos.ChangeEmailRequest;
import com.kafka.auth.dto.AuthDtos.EmailCodeResponse;
import com.kafka.auth.dto.AuthDtos.EmailLoginRequest;
import com.kafka.auth.dto.AuthDtos.LoginRequest;
import com.kafka.auth.dto.AuthDtos.NotificationSettingsResponse;
import com.kafka.auth.dto.AuthDtos.RegisterRequest;
import com.kafka.auth.dto.AuthDtos.UserResponse;
import com.kafka.auth.email.EmailVerificationMailService;
import com.kafka.auth.email.EmailVerificationProperties;
import com.kafka.auth.email.EmailVerificationThrottleService;
import com.kafka.auth.model.AuthProvider;
import com.kafka.auth.model.EmailVerificationCode;
import com.kafka.auth.model.PasswordResetToken;
import com.kafka.auth.model.UserAccount;
import com.kafka.auth.repository.EmailVerificationCodeRepository;
import com.kafka.auth.repository.PasswordResetTokenRepository;
import com.kafka.auth.repository.UserAccountRepository;
import com.kafka.auth.security.JwtService;
import com.kafka.auth.storage.StorageUrlSigner;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    /** 재설정 링크 유효 시간. 이메일 OTP(5분)보다 길게 잡아 메일 확인 지연을 흡수한다. */
    private static final Duration PASSWORD_RESET_TTL = Duration.ofMinutes(30);

    private final UserAccountRepository userAccountRepository;
    private final EmailVerificationCodeRepository emailVerificationCodeRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailVerificationMailService emailVerificationMailService;
    private final EmailVerificationProperties emailVerificationProperties;
    private final EmailVerificationThrottleService emailVerificationThrottleService;
    private final StorageUrlSigner storageUrlSigner;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userAccountRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }
        UserAccount user = userAccountRepository.save(new UserAccount(
                request.email(),
                request.name(),
                passwordEncoder.encode(request.password()),
                AuthProvider.LOCAL
        ));
        return issueToken(user);
    }

    /** 회원가입 흐름의 이메일 인증 단계: 로그인/계정생성 없이 코드만 검증한다. */
    @Transactional
    public void verifyEmailForSignup(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        if (userAccountRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }
        verifyEmailCode(normalizedEmail, code);
    }

    /**
     * 비밀번호 재설정 링크 요청. 계정 존재 여부와 무관하게 항상 성공 처리해 이메일
     * 열거(enumeration)를 막는다. 실제 계정이 있고 비밀번호 로그인 계정(소셜 아님)일
     * 때만 단발성 토큰을 발급하고 링크를 발송한다.
     */
    @Transactional
    public void requestPasswordReset(String emailRaw, String frontendBaseUrl) {
        String email = normalizeEmail(emailRaw);
        userAccountRepository.findByEmail(email).ifPresent(user -> {
            if (user.getPasswordHash() == null) {
                // 소셜 로그인 계정은 비밀번호가 없어 재설정 대상이 아니다.
                return;
            }
            String token = generateResetToken();
            Instant expiresAt = Instant.now().plus(PASSWORD_RESET_TTL);
            passwordResetTokenRepository.markUnusedTokensAsUsed(email);
            passwordResetTokenRepository.save(new PasswordResetToken(email, hashToken(token), expiresAt));
            String link = buildResetLink(frontendBaseUrl, token);
            emailVerificationMailService.sendPasswordResetLink(email, link, expiresAt);
        });
    }

    /** 재설정 링크의 토큰을 검증하고 새 비밀번호를 저장한다(토큰은 1회용). */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findTopByTokenAndUsedFalseOrderByExpiresAtDesc(hashToken(token))
                .orElseThrow(() -> new IllegalArgumentException("재설정 링크가 올바르지 않거나 이미 사용되었습니다."));
        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("재설정 링크가 만료되었습니다. 다시 요청해주세요.");
        }
        UserAccount user = userAccountRepository.findByEmail(resetToken.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다."));
        user.updatePassword(passwordEncoder.encode(newPassword));
        userAccountRepository.save(user);
        resetToken.markUsed();
        passwordResetTokenRepository.markUnusedTokensAsUsed(resetToken.getEmail());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        UserAccount user = userAccountRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다."));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        return issueToken(user);
    }

    @Transactional
    public EmailCodeResponse createEmailCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        emailVerificationThrottleService.acquireSendPermit(normalizedEmail);
        try {
            String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
            Instant expiresAt = Instant.now().plus(emailVerificationProperties.getTtl());
            emailVerificationCodeRepository.markUnusedCodesAsUsed(normalizedEmail);
            emailVerificationCodeRepository.save(new EmailVerificationCode(normalizedEmail, hashCode(normalizedEmail, code), expiresAt));
            emailVerificationMailService.sendVerificationCode(normalizedEmail, code, expiresAt);
            emailVerificationThrottleService.clearVerifyFailures(normalizedEmail);
            return new EmailCodeResponse(expiresAt, maskedEmail(normalizedEmail));
        } catch (RuntimeException exception) {
            emailVerificationThrottleService.releaseSendPermit(normalizedEmail);
            throw exception;
        }
    }

    @Transactional
    public AuthResponse loginWithEmailCode(EmailLoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        verifyEmailCode(normalizedEmail, request.code());

        UserAccount user = userAccountRepository.findByEmail(normalizedEmail)
                .orElseGet(() -> userAccountRepository.save(new UserAccount(
                        normalizedEmail,
                        request.name() == null || request.name().isBlank() ? normalizedEmail : request.name(),
                        null,
                        AuthProvider.EMAIL
                )));
        return issueToken(user);
    }

    /**
     * Sends a verification code to the address the signed-in user wants to switch to.
     * Rejects up-front if it is unchanged or already taken; the code is bound to the
     * new email, so only someone who controls it can complete {@link #changeEmail}.
     */
    @Transactional
    public EmailCodeResponse sendEmailChangeCode(UserAccount user, String newEmailRaw) {
        String newEmail = normalizeEmail(newEmailRaw);
        if (newEmail.equals(normalizeEmail(user.getEmail()))) {
            throw new IllegalArgumentException("현재 사용 중인 이메일과 동일합니다.");
        }
        if (userAccountRepository.existsByEmail(newEmail)) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }
        return createEmailCode(newEmail);
    }

    /**
     * Verifies the code for the new email, updates the account, and re-issues a JWT
     * because the token subject is the email (the old token still validates until it
     * expires, but the client should replace it with the returned one).
     */
    @Transactional
    public AuthResponse changeEmail(UserAccount user, ChangeEmailRequest request) {
        String newEmail = normalizeEmail(request.email());
        if (newEmail.equals(normalizeEmail(user.getEmail()))) {
            throw new IllegalArgumentException("현재 사용 중인 이메일과 동일합니다.");
        }
        if (userAccountRepository.existsByEmail(newEmail)) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }
        verifyEmailCode(newEmail, request.code());
        user.changeEmail(newEmail);
        return issueToken(userAccountRepository.save(user));
    }

    @Transactional
    public AuthResponse loginWithKakao(String kakaoId, String email, String name) {
        String fallbackEmail = "kakao-" + kakaoId + "@kakao.local";
        String resolvedEmail = email == null || email.isBlank() ? fallbackEmail : email;
        String resolvedName = name == null || name.isBlank() ? resolvedEmail : name;

        UserAccount user = userAccountRepository.findByProviderAndProviderId(AuthProvider.KAKAO, kakaoId)
                .or(() -> userAccountRepository.findByEmail(resolvedEmail))
                .orElseGet(() -> userAccountRepository.save(new UserAccount(
                        resolvedEmail,
                        resolvedName,
                        null,
                        AuthProvider.KAKAO
                )));
        user.setName(resolvedName);
        user.setProvider(AuthProvider.KAKAO);
        user.setProviderId(kakaoId);
        user.setPasswordHash(null);
        return issueToken(user);
    }

    @Transactional
    public AuthResponse loginWithNaver(String naverId, String email, String name) {
        String fallbackEmail = "naver-" + naverId + "@naver.local";
        String resolvedEmail = email == null || email.isBlank() ? fallbackEmail : email;
        String resolvedName = name == null || name.isBlank() ? resolvedEmail : name;

        UserAccount user = userAccountRepository.findByProviderAndProviderId(AuthProvider.NAVER, naverId)
                .or(() -> userAccountRepository.findByEmail(resolvedEmail))
                .orElseGet(() -> userAccountRepository.save(new UserAccount(
                        resolvedEmail,
                        resolvedName,
                        null,
                        AuthProvider.NAVER
                )));
        user.setName(resolvedName);
        user.setProvider(AuthProvider.NAVER);
        user.setProviderId(naverId);
        user.setPasswordHash(null);
        return issueToken(user);
    }

    /**
     * 회원 탈퇴. users 행은 다른 테이블에서 FK로 참조되지 않고 email 문자열로만
     * 느슨하게 연결되므로 하드 삭제해도 무결성 문제가 없다(대화/방 등 잔여 데이터는
     * 이메일 기준 orphan으로 남지만 참조 무결성에는 영향 없음). 로그인 계정만 제거해
     * 재로그인이 불가하도록 한다.
     */
    @Transactional
    public void deleteAccount(UserAccount user) {
        emailVerificationCodeRepository.deleteByEmailIgnoreCase(user.getEmail());
        userAccountRepository.delete(user);
    }

    public UserResponse toUserResponse(UserAccount user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getProvider().name(),
                user.getRole().name(),
                user.getStatusMessage(),
                storageUrlSigner.sign(user.getProfileImageUrl()),
                user.getTheme(),
                new NotificationSettingsResponse(
                        user.isDndEnabled(),
                        user.getDndStart(),
                        user.getDndEnd(),
                        user.isNotifyMessages(),
                        user.isNotifyMentionsOnly(),
                        user.isNotifyMarketing()
                )
        );
    }

    private AuthResponse issueToken(UserAccount user) {
        return new AuthResponse(jwtService.createToken(user), "Bearer", toUserResponse(user));
    }

    /** Validates a one-time email code (throttled) and consumes it, or throws with a friendly message. */
    private void verifyEmailCode(String normalizedEmail, String code) {
        emailVerificationThrottleService.assertVerifyAllowed(normalizedEmail);
        String codeHash = hashCode(normalizedEmail, code);
        EmailVerificationCode verificationCode = emailVerificationCodeRepository
                .findTopByEmailAndCodeAndUsedFalseOrderByExpiresAtDesc(normalizedEmail, codeHash)
                .orElse(null);
        if (verificationCode == null) {
            emailVerificationThrottleService.recordVerifyFailure(normalizedEmail);
            throw new IllegalArgumentException("인증코드가 올바르지 않습니다.");
        }
        if (verificationCode.getExpiresAt().isBefore(Instant.now())) {
            emailVerificationThrottleService.recordVerifyFailure(normalizedEmail);
            throw new IllegalArgumentException("인증코드가 만료되었습니다.");
        }
        verificationCode.markUsed();
        emailVerificationThrottleService.clearVerifyFailures(normalizedEmail);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String hashCode(String email, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest((email + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 해시를 사용할 수 없습니다.", exception);
        }
    }

    private String generateResetToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 해시를 사용할 수 없습니다.", exception);
        }
    }

    private String buildResetLink(String frontendBaseUrl, String token) {
        String base = frontendBaseUrl == null || frontendBaseUrl.isBlank()
                ? "http://localhost:8880"
                : frontendBaseUrl.trim().replaceAll("/+$", "");
        return base + "/?reset=" + token;
    }

    private String maskedEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return email;
        }
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        int visibleLength = Math.min(2, localPart.length());
        return localPart.substring(0, visibleLength) + "*".repeat(Math.max(1, localPart.length() - visibleLength)) + domain;
    }
}
