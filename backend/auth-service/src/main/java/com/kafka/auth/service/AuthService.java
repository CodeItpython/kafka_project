package com.kafka.auth.service;

import com.kafka.auth.dto.AuthDtos.AuthResponse;
import com.kafka.auth.dto.AuthDtos.EmailCodeResponse;
import com.kafka.auth.dto.AuthDtos.EmailLoginRequest;
import com.kafka.auth.dto.AuthDtos.LoginRequest;
import com.kafka.auth.dto.AuthDtos.RegisterRequest;
import com.kafka.auth.dto.AuthDtos.UserResponse;
import com.kafka.auth.model.AuthProvider;
import com.kafka.auth.model.EmailVerificationCode;
import com.kafka.auth.model.UserAccount;
import com.kafka.auth.repository.EmailVerificationCodeRepository;
import com.kafka.auth.repository.UserAccountRepository;
import com.kafka.auth.security.JwtService;
import java.security.SecureRandom;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserAccountRepository userAccountRepository;
    private final EmailVerificationCodeRepository emailVerificationCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserAccountRepository userAccountRepository,
            EmailVerificationCodeRepository emailVerificationCodeRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.emailVerificationCodeRepository = emailVerificationCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

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
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        Instant expiresAt = Instant.now().plusSeconds(300);
        emailVerificationCodeRepository.save(new EmailVerificationCode(email, code, expiresAt));
        log.info("Email verification code for {} is {}. It expires at {}.", email, code, expiresAt);
        return new EmailCodeResponse(expiresAt, code);
    }

    @Transactional
    public AuthResponse loginWithEmailCode(EmailLoginRequest request) {
        EmailVerificationCode verificationCode = emailVerificationCodeRepository
                .findTopByEmailAndCodeAndUsedFalseOrderByExpiresAtDesc(request.email(), request.code())
                .orElseThrow(() -> new IllegalArgumentException("인증코드가 올바르지 않습니다."));
        if (verificationCode.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("인증코드가 만료되었습니다.");
        }
        verificationCode.markUsed();

        UserAccount user = userAccountRepository.findByEmail(request.email())
                .orElseGet(() -> userAccountRepository.save(new UserAccount(
                        request.email(),
                        request.name() == null || request.name().isBlank() ? request.email() : request.name(),
                        null,
                        AuthProvider.EMAIL
                )));
        return issueToken(user);
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

    public UserResponse toUserResponse(UserAccount user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getProvider().name());
    }

    private AuthResponse issueToken(UserAccount user) {
        return new AuthResponse(jwtService.createToken(user), "Bearer", toUserResponse(user));
    }
}
